#if NET_4_6
using UnityEngine;
using Ionic.Zip;
using UnityEditor;
using UnityEditor.Callbacks;
using System.IO;
using System.Text.RegularExpressions;
using System.Collections;
using clojure.lang;
using System.Diagnostics;
using System.Linq;
using System.Reflection;

namespace Arcadia
{
	public static class BuildPipeline
	{
		static int buildProgressCurrent = 1;
		static float buildProgressTotal = 0;

		public static string CompiledFolder = Path.Combine("Arcadia", "Compiled");
		public static string ExportFolder = Path.Combine("Arcadia", "Export");
		public static string ExportAssetsFolder = Path.Combine("Assets", Path.Combine("Arcadia", "Export"));

		static BuildPipeline ()
		{
            Util.require("arcadia.internal.editor-interop");
			EditorUtility.ClearProgressBar();
		}

		public static void EnsureCompiledFolders ()
		{
			UnityEngine.Debug.Log("Creating Compiled Folders");
			if (!Directory.Exists(CompiledFolder))
				Directory.CreateDirectory(CompiledFolder);
		}

		public static void EnsureEmptyFolder (string folder)
		{
			if (!Directory.Exists(folder)) {
				Directory.CreateDirectory(folder);
			} else {
				Directory.Delete(folder, true);
				Directory.CreateDirectory(folder);
			}
		}

		public static void EnsureExportFolders ()
		{
			EnsureEmptyFolder(ExportFolder);
			EnsureEmptyFolder(ExportAssetsFolder);
		}

		static void ResetProgress (float newTotal)
		{
			buildProgressCurrent = 1;
			buildProgressTotal = newTotal;
			EditorUtility.ClearProgressBar();
		}

		static bool Progress (string message)
		{
			return Progress("Arcadia", message);
		}

		static bool Progress (string title, string message)
		{
			return EditorUtility.DisplayCancelableProgressBar(title, message, buildProgressCurrent++ / buildProgressTotal);
		}

		public static bool IsAssemblyExportable (Assembly assembly)
		{
			return IsAssemblyExportable(assembly, PersistentHashSet.EMPTY);
		}

		public static bool IsAssemblyExportable (Assembly assembly, IPersistentSet checkedAssemblies)
		{
			if (checkedAssemblies.contains(assembly))
				return true;

			foreach (var referencedAssembly in assembly.GetReferencedAssemblies()) {
				if (referencedAssembly.Name == "UnityEditor" || referencedAssembly.Name == "Assembly-CSharp-Editor") {
					return false;
				}

				if (!IsAssemblyExportable(Assembly.Load(referencedAssembly), (IPersistentSet)checkedAssemblies.cons(assembly))) {
					return false;
				}
			}

			return true;
		}

		static string NamespaceFileName (string name)
		{
			return name.Replace("-", "_") + ".clj.dll";
		}

		static string NamespaceFileName (Symbol name)
		{
			return NamespaceFileName(name.ToString());
		}

		static string NamespaceFileName (Namespace ns)
		{
			return NamespaceFileName(ns.Name);
		}

		static Var aotNamespacesVar;

		public class NsProgressReporter : clojure.lang.AFn
		{
			public override object invoke (object arg1)
			{
				Symbol nsSymbol = (Symbol)arg1;
				Progress("Arcadia", "Compiling " + nsSymbol);
				return null;
			}
		}

		static void CompileNamespacesToFolder (IEnumerable userNameSpaces, string targetFolder)
		{
			// Initialization.SetBuildClojureLoadPath();

			ResetProgress(userNameSpaces.Cast<object>().Count());
			try {
				string compilerNs = "arcadia.internal.compiler";
				Arcadia.Util.require(compilerNs);
				Arcadia.Util.getVar(ref aotNamespacesVar, compilerNs, "aot-namespaces");
				Arcadia.Util.Invoke(
					aotNamespacesVar,
					targetFolder,
					userNameSpaces,
					RT.mapUniqueKeys(new object[] {
						Keyword.intern(Symbol.intern("file-callback")),
						new NsProgressReporter()
					})
				);
			} finally {
				EditorUtility.ClearProgressBar();
			}
		}

		public static void BuildInternal ()
		{
			EnsureCompiledFolders();
			var internalNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "internal-namespaces").deref();
			CompileNamespacesToFolder(internalNameSpaces, CompiledFolder);
		}

		public static void BuildUser ()
		{
			EnsureCompiledFolders();
			var userNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke();
			CompileNamespacesToFolder(userNameSpaces, CompiledFolder);
		}

		public static void PrepareExport ()
		{
			EnsureExportFolders();

			var oldLoadPath = System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH");
			try {
				var newLoadPath = oldLoadPath.Replace(Path.GetFullPath(CompiledFolder) + Path.PathSeparator, "");
				UnityEngine.Debug.Log("newLoadPath: " + newLoadPath);
				System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", newLoadPath);

				var userNamespaces = ((IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke()).Cast<Symbol>();

				CompileNamespacesToFolder(userNamespaces, ExportFolder);

				var filesToExport = Directory.GetFiles(ExportFolder);

				foreach (var src in filesToExport) {
					var filename = Path.GetFileName(src);
					var dst = Path.Combine(ExportAssetsFolder, filename);
					File.Copy(src, dst);
				}

				UnityEngine.Debug.Log("Ready to build!");
			} finally {
				System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", oldLoadPath);
			}
			AssetDatabase.Refresh(ImportAssetOptions.Default);
		}

		public static void CleanCompiled ()
		{
			if (Directory.Exists(CompiledFolder))
				Directory.Delete(CompiledFolder, true);
		}

		[PostProcessBuild(1)]
		public static void OnPostprocessBuild (BuildTarget target, string pathToBuiltProject)
		{
			if (Directory.Exists(ExportFolder))
				Directory.Delete(ExportFolder, true);
			if (Directory.Exists(ExportAssetsFolder))
				Directory.Delete(ExportAssetsFolder, true);
		}
	}
}
#endif