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

        static BuildPipeline()
        {
            RT.load("arcadia/internal/editor_interop");
            EditorUtility.ClearProgressBar();
        }

        public static void EnsureCompiledFolders()
        {
            UnityEngine.Debug.Log("Creating Compiled Folders");
            if (!Directory.Exists(CompiledFolder))
                Directory.CreateDirectory(CompiledFolder);
        }

        public static void EnsureEmptyFolder(string folder)
        {
			if (!Directory.Exists(folder))
			{
				Directory.CreateDirectory(folder);
			}
			else
			{
				Directory.Delete(folder, true);
				Directory.CreateDirectory(folder);
			}
		}

        public static void EnsureExportFolders()
        {
            EnsureEmptyFolder(ExportFolder);
            EnsureEmptyFolder(ExportAssetsFolder);
        }

        static void ResetProgress(float newTotal)
        {
            buildProgressCurrent = 1;
            buildProgressTotal = newTotal;
            EditorUtility.ClearProgressBar();
        }

        static bool Progress(string message)
        {
            return Progress("Arcadia", message);
        }

        static bool Progress(string title, string message)
        {
            return EditorUtility.DisplayCancelableProgressBar(title, message, buildProgressCurrent++ / buildProgressTotal);
        }

        public static bool IsAssemblyExportable(Assembly assembly)
        {
            return IsAssemblyExportable(assembly, PersistentHashSet.EMPTY);
        }

        public static bool IsAssemblyExportable(Assembly assembly, IPersistentSet checkedAssemblies)
        {
            if (checkedAssemblies.contains(assembly))
                return true;

            foreach (var referencedAssembly in assembly.GetReferencedAssemblies())
            {
                if (referencedAssembly.Name == "UnityEditor" || referencedAssembly.Name == "Assembly-CSharp-Editor")
                {
                    return false;
                }

                if (!IsAssemblyExportable(Assembly.Load(referencedAssembly), (IPersistentSet)checkedAssemblies.cons(assembly)))
                {
                    return false;
                }
            }

            return true;
        }

        static string NamespaceFileName(string name)
        {
            return name.Replace("-", "_") + ".clj.dll";
        }

        static string NamespaceFileName(Symbol name)
        {
            return NamespaceFileName(name.ToString());
        }

        static string NamespaceFileName(Namespace ns)
        {
            return NamespaceFileName(ns.Name);
        }

        static void CompileNamespacesToFolder(IEnumerable userNameSpaces, string targetFolder)
        {
            // Initialization.SetBuildClojureLoadPath();
            string nsString = "";
            try
            {
                foreach (var nsSymbol in userNameSpaces)
                {
                    nsString = nsSymbol.ToString();
                    var dllFileName = NamespaceFileName(nsString);
                    if (!File.Exists(Path.Combine(targetFolder, dllFileName)))
                    {
                        Progress("Arcadia", "Compiling " + nsSymbol);
                        RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, nsSymbol);
                    }
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogError("Failed to compile namespace " + nsString);
                UnityEngine.Debug.LogException(e);
                EditorUtility.ClearProgressBar();
            }
            //Initialization.SetInitialClojureLoadPath();
            EditorUtility.ClearProgressBar();
        }

        [MenuItem("Arcadia/Build/Internal Namespaces")]
        static void BuildInternal()
        {
            EnsureCompiledFolders();
            var internalNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "internal-namespaces").deref();
            ResetProgress(internalNameSpaces.Count);
            CompileNamespacesToFolder(internalNameSpaces, CompiledFolder);
        }

        [MenuItem("Arcadia/Build/User Namespaces")]
        static void BuildUser()
        {
            EnsureCompiledFolders();
            var userNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke();
            ResetProgress(userNameSpaces.Count);
            CompileNamespacesToFolder(userNameSpaces, CompiledFolder);
        }

        [MenuItem("Arcadia/Build/Prepare Export")]
        static void PrepareExport()
        {
            EnsureExportFolders();

            var oldLoadPath = System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH");
            var newLoadPath = oldLoadPath.Replace(Path.GetFullPath(CompiledFolder) + Path.PathSeparator, "");
            UnityEngine.Debug.Log("newLoadPath: " + newLoadPath);
            System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", newLoadPath);

            var userNamespaces = ((IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke()).Cast<Symbol>();

            ResetProgress(userNamespaces.Count());
            CompileNamespacesToFolder(userNamespaces, ExportFolder);

            var filesToExport = Directory.GetFiles(ExportFolder);
            ResetProgress(filesToExport.Length);
            foreach (var src in filesToExport)
            {
                var filename = Path.GetFileName(src);
                var dst = Path.Combine(ExportAssetsFolder, filename);
                if (/* IsAssemblyExportable(Assembly.LoadFile(src))*/ true)
                {
                    Progress("Arcadia", "Copying " + filename);
                    File.Copy(src, dst);
                }
                else
                {
                    UnityEngine.Debug.LogError("Assembly " + filename + " is not exportable, quitting.");
                    EditorUtility.ClearProgressBar();
                }
            }

            EditorUtility.ClearProgressBar();
            UnityEngine.Debug.Log("Ready to build!");
            // AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", oldLoadPath);
        }
        
        [MenuItem("Arcadia/Build/Clean Compiled", false, 30)]
        static void CleanCompiled()
        {
             if (Directory.Exists(CompiledFolder))
                 Directory.Delete(CompiledFolder, true);
       }

        [PostProcessBuild(1)]
        public static void OnPostprocessBuild(BuildTarget target, string pathToBuiltProject)
        {
             if (Directory.Exists(ExportFolder))
                 Directory.Delete(ExportFolder, true);
             if (Directory.Exists(ExportAssetsFolder))
                 Directory.Delete(ExportAssetsFolder, true);
        }
    }
}