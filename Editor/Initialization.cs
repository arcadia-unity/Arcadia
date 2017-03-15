using System;
using System.IO;
using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	[InitializeOnLoad]
	public class Initialization
	{
		static Initialization()
		{
			Initialize();
		}

		public static String GetClojureDllFolder()
		{
			try
			{
				return
				  Path.GetDirectoryName(
					AssetDatabase.GetAllAssetPaths()
					  .Where(s => System.Text.RegularExpressions.Regex.IsMatch(s, ".*/Clojure.dll$")) // for compatibility with 4.3
					  .Single());
			}
			catch (InvalidOperationException e)
			{
				throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
			}
		}

		public static void ensureCompiledFolder()
		{
			string maybeCompiled = Path.GetFullPath(VariadicPathCombine(GetClojureDllFolder(), "..", "Compiled"));
			if (!Directory.Exists(maybeCompiled))
			{
				Debug.Log("Creating Compiled");
				Directory.CreateDirectory(maybeCompiled);
			}
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
			RT.load("arcadia/literals");
		}

		[MenuItem("Arcadia/Initialization/Rerun")]
		public static void Initialize()
		{
			Debug.Log("Starting Arcadia...");

			CheckSettings();
			SetInitialClojureLoadPath();
			LoadConfig();
			LoadPackages();
			LoadLiterals();
			SetClojureLoadPath();
			ensureCompiledFolder();
			StartEditorCallbacks();
			StartWatching();
			StartREPL();

			Debug.Log("Arcadia Started!");
		}

		// code is so durn orthogonal we have to explicitly call this
		// (necessary for package-sensitive loadpaths in presence of stuff like leiningen)
		// on the other hand, packages pulls in almost everything else
		public static void LoadPackages(){
			Debug.Log("Loading packages...");
			RT.load("arcadia/packages");
			// may want to make this conditional on some config thing
			RT.var("arcadia.packages", "install-all-deps").invoke();
		}

		[MenuItem("Arcadia/Initialization/Load Configuration")]
		public static void LoadConfig()
		{
			Debug.Log("Loading configuration...");
			RT.load("arcadia/config");
			RT.var("arcadia.config", "update!").invoke();
		}

		[MenuItem("Arcadia/Initialization/Setup Player Settings")]
		public static void CheckSettings()
		{
			Debug.Log("Checking Unity Settings...");
			if (PlayerSettings.apiCompatibilityLevel != ApiCompatibilityLevel.NET_2_0)
			{
				Debug.Log("Updating API Compatibility Level to .NET 20");
				PlayerSettings.apiCompatibilityLevel = ApiCompatibilityLevel.NET_2_0;
			}

			if (!PlayerSettings.runInBackground)
			{
				Debug.Log("Updating Run In Background to true");
				PlayerSettings.runInBackground = true;
			}
		}
		
		[MenuItem("Arcadia/Compiler/AOT Compile Internal Namespaces")]
		public static void AOTInternalNamespaces()
		{
			RT.load("arcadia/internal/editor_interop");
			RT.var("arcadia.internal.editor-interop", "aot-internal-namespaces").invoke("Assets/Arcadia/Compiled");
			AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);
		}

		// need this to set things up so we can get rest of loadpath after loading arcadia.compiler
		public static void SetInitialClojureLoadPath()
		{
			try
			{
				Debug.Log("Setting Initial Load Path...");
				string clojureDllFolder = GetClojureDllFolder();

				Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
				  Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Compiled")) + Path.PathSeparator +
				  Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Source")) + Path.PathSeparator +
				  Path.GetFullPath(Application.dataPath));
			}
			catch (InvalidOperationException e)
			{
				throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
			}

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
		}


		[MenuItem("Arcadia/Initialization/Update Clojure Load Path")]
		public static void SetClojureLoadPath()
		{
			Debug.Log("Setting Load Path...");
			string clojureDllFolder = GetClojureDllFolder();

			Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Compiled")) + Path.PathSeparator +
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Source")) + Path.PathSeparator +
				Path.GetFullPath(Application.dataPath) + Path.PathSeparator +
				RT.var("arcadia.compiler", "loadpath-extension-string").invoke() + Path.PathSeparator +
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Libraries")));

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
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
			RT.load("arcadia/internal/editor_callbacks");
			EditorCallbacks.Initialize();
		}
	}
}