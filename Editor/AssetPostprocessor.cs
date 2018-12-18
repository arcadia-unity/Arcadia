#if NET_4_6
using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
using System.IO;
using System.Collections.Generic;
using System.Linq;

namespace Arcadia
{
	class AssetPostprocessor : UnityEditor.AssetPostprocessor
	{
		// HACK waiting on filewatcher
		public static HashSet<string> cljFiles;

		static AssetPostprocessor()
		{
            Util.require("arcadia.internal.compiler");
			// kill repl when exiting unity
			AppDomain.CurrentDomain.ProcessExit += (object sender, EventArgs e) => { StopWatchingFiles(); };

			if (cljFiles == null)
			{
				cljFiles = new HashSet<string>();
				foreach (var file in Directory.GetFiles("Assets", "*.clj", SearchOption.AllDirectories))
				{
					cljFiles.Add(file);
				}
			}

		}

		static void OnPostprocessAllAssets(string[] importedAssets, string[] deletedAssets, string[] movedAssets, string[] movedFromAssetPaths)
		{
			foreach (var file in importedAssets.Where(s => s.EndsWith(".clj")))
			{
				cljFiles.Add(file);
			}

			foreach (var file in deletedAssets.Where(s => s.EndsWith(".clj")))
			{
				cljFiles.Remove(file);
			}
		}

		static public void StartWatchingFiles()
		{
			RT.var("arcadia.internal.compiler", "start-watching-files").invoke();
			EditorApplication.update += AssetPostprocessor.ImportChangedFiles;
		}

		static public void StopWatchingFiles()
		{
			RT.var("arcadia.internal.compiler", "stop-watching-files").invoke();
			EditorApplication.update -= AssetPostprocessor.ImportChangedFiles;
		}

		static public void ImportChangedFiles()
		{
			RT.var("arcadia.internal.compiler", "import-changed-files").invoke();
		}

		public static void ReloadAssets()
		{
			AssetDatabase.Refresh();
		}

		public static void ForceReloadAssets()
		{
			AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);
		}
	}
}
#endif