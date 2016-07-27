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

namespace Arcadia
{
	public static class BuildPostProcess
	{
		static int buildProgressCurrent = 0;
		static float buildProgressTotal = 0;

		static void ResetProgress(float newTotal)
		{
			buildProgressCurrent = 0;
			buildProgressTotal = newTotal;
			EditorUtility.ClearProgressBar();
		}

		static bool Progress(string message)
		{
			return Progress("Arcadia", message);
		}

		static bool Progress(string title, string message)
		{
			UnityEngine.Debug.Log(title + " " + message + " : " + buildProgressCurrent + "/" + buildProgressTotal);
			return EditorUtility.DisplayCancelableProgressBar(title, message, buildProgressCurrent++ / buildProgressTotal);
		}

		static void CompileNamespacesToFolder(IList userNameSpaces, string targetFolder)
		{
			foreach (var nsSymbol in userNameSpaces)
			{
				Progress("Arcadia", "Compiling " + nsSymbol);
				RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, nsSymbol);
			}

			Progress("Arcadia", "Compiling arcadia.core");
			RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("arcadia.core"));

			Progress("Arcadia", "Compiling clojure.core");
			RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("clojure.core"));

			// TODO get rid of data_readers.clj?
			Progress("Arcadia", "Copying data_readers.clj");
			File.Copy(RT.FindFile("data_readers.clj").FullName, targetFolder + "/data_readers.clj");

		}

		[PostProcessBuild(1)]
		public static void OnPostprocessBuild(BuildTarget target, string pathToBuiltProject)
		{
			RT.load("arcadia/internal/editor_interop");
			RT.load("arcadia/compiler");
			var userNameSpaces = (IList)RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke();

			if (target == BuildTarget.StandaloneOSXUniversal || target == BuildTarget.StandaloneOSXIntel || target == BuildTarget.StandaloneOSXIntel64)
			{
				ResetProgress(userNameSpaces.Count + 3);
				CompileNamespacesToFolder(userNameSpaces, Initialization.VariadicPathCombine(pathToBuiltProject, "Contents", "Resources", "Data", "Managed"));

			}
			else if (target == BuildTarget.StandaloneWindows || target == BuildTarget.StandaloneWindows64)
			{
				ResetProgress(userNameSpaces.Count + 3);
				var dataPath = pathToBuiltProject.Replace(".exe", "_Data");
				CompileNamespacesToFolder(userNameSpaces, Path.Combine(dataPath, "Managed"));

			}
			else if (target == BuildTarget.StandaloneLinux || target == BuildTarget.StandaloneLinux64 || target == BuildTarget.StandaloneLinuxUniversal)
			{
				ResetProgress(userNameSpaces.Count + 3);
				var linuxBuildEnding = new Regex("\\..*$");
				var dataPath = linuxBuildEnding.Replace(pathToBuiltProject, "_Data");
				CompileNamespacesToFolder(userNameSpaces, Path.Combine(dataPath, "Managed"));
			}
			else if (target == BuildTarget.Android)
			{
				ResetProgress(userNameSpaces.Count + 3);
				var targetPath = Path.Combine(Directory.GetParent(pathToBuiltProject).FullName, pathToBuiltProject + "-clj-build");  // Initialization.VariadicPathCombine(Directory.GetParent(pathToBuiltProject).FullName, "assets", "bin", "Data", "Managed");
				var signedApk = pathToBuiltProject + "-signed";
				var alignedApk = pathToBuiltProject + "-aligned";
				Directory.CreateDirectory(targetPath);
				CompileNamespacesToFolder(userNameSpaces, targetPath);

				// add *.clj.dll files to apk
				var cljFiles = Directory.GetFiles(targetPath);
				ResetProgress(cljFiles.Length + 3);
				var apk = ZipFile.Read(pathToBuiltProject);
				foreach (var file in cljFiles)
				{
					Progress("Arcadia : Android", "Adding " + Path.GetFileName(file) + " to APK");
					apk.AddFile(file, "assets/bin/Data/Managed");
				}
				apk.Save();

				// sign jar
				Progress("Arcadia : Android", "Signing APK");
				var jdkPathString = EditorPrefs.GetString("JdkPath");
				if (string.IsNullOrEmpty(jdkPathString))
				{
					UnityEngine.Debug.LogError("JDK path not set, aborting Arcadia export");
					return;
				}

#if UNITY_EDITOR_WIN
				var debugKeystorePath = "%USERPROFILE%\\.android\\debug.keystore";
#else
				var debugKeystorePath = "~/.android/debug.keystore";
#endif
				var keystorePath = string.IsNullOrEmpty(PlayerSettings.Android.keystoreName) ? debugKeystorePath : PlayerSettings.Android.keystoreName;
				var keystorePass = string.IsNullOrEmpty(PlayerSettings.Android.keystorePass) ? "android" : PlayerSettings.Android.keystorePass;
				var keyaliasName = string.IsNullOrEmpty(PlayerSettings.Android.keyaliasName) ? "androiddebugkey" : PlayerSettings.Android.keyaliasName;
				var keyaliasPass = string.IsNullOrEmpty(PlayerSettings.Android.keyaliasPass) ? "android" : PlayerSettings.Android.keyaliasPass;
				// TODO is it jarsigner.exe on windows?
				var jarsignerPath = Initialization.VariadicPathCombine(jdkPathString, "bin", "jarsigner");
				var jarsignerArgs = "-verbose -sigfile cert" +
					" -keystore " + keystorePath +
					" -storepass " + keystorePass +
					" -signedjar " + signedApk +
					" -keypass " + keyaliasPass +
					" " + pathToBuiltProject + " " + keyaliasName;
				Process.Start(jarsignerPath, jarsignerArgs);

				// zipalign
				Progress("Arcadia : Android", "Zip Aligning APK");
				var AndroidSDKTools = Types.GetType("UnityEditor.Android.AndroidSDKTools", "UnityEditor.Android.Extensions");
				var androidSDKToolsGetInstanceMethod = AndroidSDKTools.GetMethod("GetInstance");
				var androidSDKToolsInstance = androidSDKToolsGetInstanceMethod.Invoke(null, null);
				var zipalignProperty = AndroidSDKTools.GetProperty("ZIPALIGN");
				var zipalignPath = (string)zipalignProperty.GetValue(androidSDKToolsInstance, null);
				var zipalignArgs = "-f -v 4 " + signedApk + " " + alignedApk;
				Process.Start(zipalignPath, zipalignArgs);

				Progress("Arcadia : Android", "Cleaning Up");
				// File.Delete(signedApk);
				// File.Delete(pathToBuiltProject);
				// File.Move(alignedApk, pathToBuiltProject);
				// Directory.Delete(targetPath, true);
			}
			else {
				EditorUtility.DisplayDialog(
				  "Unsupported Export Target", "Arcadia does not yet support export to the target " + target, "OK");
			}

			EditorUtility.ClearProgressBar();
		}
	}
}