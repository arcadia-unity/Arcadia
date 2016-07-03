using UnityEngine;
using UnityEditor;
using UnityEditor.Callbacks;
using System.IO;
using System.Text.RegularExpressions;
using System.Collections;
using clojure.lang;

namespace Arcadia
{
	public class BuildPostProcess
	{
		static float buildProgress = 0;

		static bool Progress(string message, int total)
		{
			return EditorUtility.DisplayCancelableProgressBar("Exporting Arcadia", message, buildProgress++ / total);
		}

		static void CompileNamespacesToFolder(string targetFolder)
		{
			RT.load("arcadia/internal/editor_interop");
			RT.load("arcadia/compiler");
			var userNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke();

			foreach (var nsSymbol in userNameSpaces)
			{
				Progress("Compiling " + nsSymbol, userNameSpaces.Count + 3);
				RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, nsSymbol);
			}

			Progress("Compiling arcadia.core", userNameSpaces.Count + 3);
			RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("arcadia.core"));

			Progress("Compiling clojure.core", userNameSpaces.Count + 3);
			RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("clojure.core"));

			Progress("Copying data_readers.clj", userNameSpaces.Count + 3);
			File.Copy(RT.FindFile("data_readers.clj").FullName, targetFolder + "/data_readers.clj");

			EditorUtility.ClearProgressBar();
		}

		[PostProcessBuildAttribute(1)]
		public static void OnPostprocessBuild(BuildTarget target, string pathToBuiltProject)
		{
			buildProgress = 0;
			if (target == BuildTarget.StandaloneOSXUniversal || target == BuildTarget.StandaloneOSXIntel || target == BuildTarget.StandaloneOSXIntel64)
			{
				CompileNamespacesToFolder(Path.Combine(pathToBuiltProject, "Contents/Resources/Data/Managed"));

			}
			else if (target == BuildTarget.StandaloneWindows || target == BuildTarget.StandaloneWindows64)
			{
				var dataPath = pathToBuiltProject.Replace(".exe", "_Data");
				CompileNamespacesToFolder(Path.Combine(dataPath, "Managed"));

			}
			else if (target == BuildTarget.StandaloneLinux || target == BuildTarget.StandaloneLinux64 || target == BuildTarget.StandaloneLinuxUniversal)
			{
				var linuxBuildEnding = new Regex("\\..*$");
				var dataPath = linuxBuildEnding.Replace(pathToBuiltProject, "_Data");
				CompileNamespacesToFolder(Path.Combine(dataPath, "Managed"));

			}
			else {
				EditorUtility.DisplayDialog(
				  "Unsupported Export Target", "Arcadia does not yet support export to the target " + target, "OK");
			}
		}
	}
}