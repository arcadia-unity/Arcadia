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
		public static string ExportAssetsFolder = Path.Combine(BasicPaths.ArcadiaFolder, "Export");

		static BuildPipeline ()
		{
            Util.require("arcadia.internal.editor-interop");
            Util.require("arcadia.internal.config");
			//EditorUtility.ClearProgressBar();
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

        public static void BuildAll ()
        {
            EnsureCompiledFolders();
            IList internalAndUserNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "internal-and-user-aot-root-namespaces").invoke();
            CompileNamespacesToFolder(internalAndUserNameSpaces, CompiledFolder);
        }

		public static void PrepareExport ()
		{
			EnsureExportFolders();

			var oldLoadPath = System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH");
			try {
				var newLoadPath = oldLoadPath.Replace(Path.GetFullPath(CompiledFolder) + Path.PathSeparator, "");
				UnityEngine.Debug.Log("newLoadPath: " + newLoadPath);
				System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", newLoadPath);

				var userNamespaces = ((IList)RT.var("arcadia.internal.editor-interop", "user-export-namespaces-symbols").invoke()).Cast<Symbol>();

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

		// https://forum.unity.com/threads/postprocessbuild-preprocessbuild.293616/
		static string GetDataManagedFolder(BuildTarget target, string pathToBuiltProject)
		{
			if (target.ToString ().Contains ("OSX"))
			{
				return pathToBuiltProject+"/Contents/Resources/Data/Managed/";
			}
			if (target.ToString ().Contains ("Windows"))
			{
				string name = Path.GetFileNameWithoutExtension(pathToBuiltProject);
				string directory = Path.GetDirectoryName(pathToBuiltProject);
				return BasicPaths.PathCombine(directory, name + "_Data", "Managed");
			}
			if (target.ToString ().Contains ("Linux"))
			{
				string name = Path.GetFileNameWithoutExtension(pathToBuiltProject);
				string directory = Path.GetDirectoryName(pathToBuiltProject);
				return BasicPaths.PathCombine(directory, name + "_Data", "Managed");
			}
			UnityEngine.Debug.Log(string.Format("Exported configuration for target {0} not supported. Configuration will not be usable in export.", target));
			return Path.GetDirectoryName(pathToBuiltProject);
		}

		[PostProcessBuild(1)]
		public static void OnPostprocessBuild (BuildTarget target, string pathToBuiltProject)
		{
			var configString = RT.var("arcadia.internal.config", "config").invoke().ToString();
			var managedFolder = GetDataManagedFolder(target, pathToBuiltProject);
			File.WriteAllText(Path.Combine(managedFolder, "exported-configuration.edn"), configString);

			if (Directory.Exists(ExportFolder))
				Directory.Delete(ExportFolder, true);
			if (Directory.Exists(ExportAssetsFolder))
				Directory.Delete(ExportAssetsFolder, true);
		}
	}
}