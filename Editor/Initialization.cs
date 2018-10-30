#if NET_4_6
using System;
using System.IO;
using System.Linq;
using System.Reflection;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	[InitializeOnLoad]
	public class Initialization
	{
		// ============================================================
		// Data
		public static string PathToCompiled = Path.GetFullPath(VariadicPathCombine(Application.dataPath, "..", "Arcadia", "Compiled"));

		public static string PathToCompiledForExport = Path.GetFullPath(VariadicPathCombine(Application.dataPath, "Arcadia", "Export"));

		static Initialization()
		{
			Initialize();
		}

		public static String GetClojureDllFolder()
		{
			return Path.GetDirectoryName(typeof(clojure.lang.RT).Assembly.Location).Substring(Directory.GetCurrentDirectory().Length + 1);
		}

		public static void StartWatching()
		{
			//AssetPostprocessor.StartWatchingFiles();
			Var config = RT.var("arcadia.config", "config");
			Var startAssetWatcher = RT.var("arcadia.internal.asset-watcher", "start-asset-watcher");
			startAssetWatcher.invoke(config.invoke());
		}

		public static void LoadLiterals()
		{
			// this has to happen here becasue the repl
			// binds a thread local *data-readers*
            Util.require("arcadia.data");
		}

		[MenuItem("Arcadia/Initialization/Rerun")]
		public static void Initialize()
		{
			Debug.Log("Starting Arcadia...");
            DisableSpecChecking();
			SetInitialClojureLoadPath();
			LoadConfig();
			LoadLiterals();
			InitializeLoadPathExtensions();
			SetClojureLoadPath();
            BuildPipeline.EnsureCompiledFolders();
			StartEditorCallbacks();
			StartWatching();
			LoadSocketREPL();
			StartREPL();
			StartNudge();
			Debug.Log("Arcadia Started!");
		}

		private static void InitializeLoadPathExtensions()
		{
			Util.require("arcadia.internal.leiningen");
			Util.require("arcadia.internal.deps");
		}

		// workaround for spec issues
        static void DisableSpecChecking()
        {
        	Environment.SetEnvironmentVariable("clojure.spec.check-asserts", "false");
        	Environment.SetEnvironmentVariable("clojure.spec.skip-macros", "true");
        }

		[MenuItem("Arcadia/Initialization/Load Configuration")]
		public static void LoadConfig()
		{
			Debug.Log("Loading configuration...");
            Util.require("arcadia.config");
			RT.var("arcadia.config", "update!").invoke();
		}

		public static void StartNudge()
		{
            Util.require("arcadia.internal.nudge");
		}

		public static string InitialClojureLoadPath ()
		{
			var path = PathToCompiled + Path.PathSeparator +
					Path.GetFullPath(VariadicPathCombine(GetClojureDllFolder(), "..", "Source")) + Path.PathSeparator +
				  Path.GetFullPath(Application.dataPath);
			return path;
		}

		// need this to set things up so we can get rest of loadpath after loading arcadia.compiler
		public static void SetInitialClojureLoadPath()
		{
			try
			{
				Debug.Log("Setting Initial Load Path...");
				Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", InitialClojureLoadPath());

			}
			catch (InvalidOperationException e)
			{
				throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
			}

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
		}
		
		[MenuItem("Arcadia/Initialization/Update Clojure Load Path")]
		public static void SetClojureLoadPath ()
		{
			Util.require("arcadia.compiler");
			Debug.Log("Setting Load Path...");
			string clojureDllFolder = GetClojureDllFolder();

			Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
				InitialClojureLoadPath() + Path.PathSeparator +
				RT.var("arcadia.compiler", "loadpath-extension-string").invoke() + Path.PathSeparator +
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Libraries")));

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
		}
		
		static void LoadSocketREPL ()
		{
            Util.require("arcadia.socket-repl");
			RT.var("arcadia.socket-repl", "server-reactive").invoke();
		}

		static void StartREPL()
		{
			Repl.StartREPL();
		}

		// old mono...
		public static string VariadicPathCombine(params string[] paths)
		{
			string path = "";
			foreach (string p in paths)
			{
				path = Path.Combine(path, p);
			}
			return path;
		}

		public static void StartEditorCallbacks(){
            Util.require("arcadia.internal.editor-callbacks");
			EditorCallbacks.Initialize();
		}

		// dunno where else to put this
		public static void PurgeAllCompiled ()
		{
			var compiledDir = new DirectoryInfo(PathToCompiled);
			if (compiledDir.Exists) {
				foreach (var file in compiledDir.GetFiles()) {
					file.Delete();
				}
			}
			var outerCompiledForExportDir = new DirectoryInfo(VariadicPathCombine(PathToCompiled, "..", "Export"));
			if (outerCompiledForExportDir.Exists) {
				foreach (var file in outerCompiledForExportDir.GetFiles()) {
					file.Delete();
				}
			}
			var exportDir = new DirectoryInfo(PathToCompiledForExport);
			if (exportDir.Exists) {
				foreach (var file in exportDir.GetFiles()) {
					file.Delete();
				}
			}
		}

		[MenuItem("Arcadia/Clean")]
		public static void Clean ()
		{
			PurgeAllCompiled();
		}
	}
}
#endif