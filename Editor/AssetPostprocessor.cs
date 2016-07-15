using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
using System.IO;

namespace Arcadia
{
	class AssetPostprocessor
	{
		static AssetPostprocessor()
		{
			RT.load("arcadia/compiler");
			// kill repl when exiting unity
			AppDomain.CurrentDomain.ProcessExit += (object sender, EventArgs e) => { StopWatchingFiles(); };
		}

		static public void StartWatchingFiles()
		{
			RT.var("arcadia.compiler", "start-watching-files").invoke();
			EditorApplication.update += AssetPostprocessor.ImportChangedFiles;
		}

		static public void StopWatchingFiles()
		{
			RT.var("arcadia.compiler", "stop-watching-files").invoke();
			EditorApplication.update -= AssetPostprocessor.ImportChangedFiles;
		}

		static public void ImportChangedFiles()
		{
			RT.var("arcadia.compiler", "import-changed-files").invoke();
		}

		[MenuItem("Arcadia/Compiler/Reload Assets")]
		public static void ReloadAssets()
		{
			AssetDatabase.Refresh();
		}

		[MenuItem("Arcadia/Compiler/Force Reload Assets")]
		public static void ForceReloadAssets()
		{
			AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);
		}
	}
}