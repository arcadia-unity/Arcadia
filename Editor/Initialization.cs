﻿using System;
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
			string maybeCompiled = Path.GetFullPath(VariadicPathCombine(GetClojureDllFolder(), "..", "Compiled", "Editor"));
			if (!Directory.Exists(maybeCompiled))
			{
				Debug.Log("Creating Compiled/Editor");
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
			SetInitialClojureLoadPath();
			LoadConfig();
			LoadPackages();
			LoadLiterals();
			SetClojureLoadPath();
			ensureCompiledFolder();
			StartEditorCallbacks();
			StartWatching();
			LoadSocketREPL();
			StartREPL();
			StartNudge();
			Debug.Log("Arcadia Started!");
		}


		// code is so durn orthogonal we have to explicitly call this
		// (necessary for package-sensitive loadpaths in presence of stuff like leiningen)
		// on the other hand, packages pulls in almost everything else
		public static void LoadPackages(){
			Debug.Log("Loading packages...");
			RT.load("arcadia/packages");
			if(((int)RT.var("arcadia.packages", "dependency-count").invoke()) > 0) {
				// may want to make this conditional on some config thing
				RT.var("arcadia.packages", "install-all-deps").invoke();
			}
		}

		[MenuItem("Arcadia/Initialization/Load Configuration")]
		public static void LoadConfig()
		{
			Debug.Log("Loading configuration...");
			RT.load("arcadia/config");
			RT.var("arcadia.config", "update!").invoke();
		}

		public static void StartNudge()
		{
			RT.load("arcadia/internal/nudge");
		}
		
		[MenuItem("Arcadia/Compiler/AOT Compile Internal Namespaces")]
		public static void AOTInternalNamespaces()
		{
			RT.load("arcadia/internal/editor_interop");
			RT.var("arcadia.internal.editor-interop", "aot-internal-namespaces").invoke("Assets/Arcadia/Compiled/Editor");
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
				  Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Compiled", "Editor")) + Path.PathSeparator +
				  Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Source")) + Path.PathSeparator +
				  Path.GetFullPath(Application.dataPath));
			}
			catch (InvalidOperationException e)
			{
				throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
			}

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
		}

		// resets the load path without Compiled/Editor, where AOT'd internal namespaces are kept
		public static void SetBuildClojureLoadPath()
		{
			Debug.Log("Setting Build Load Path...");
			string clojureDllFolder = GetClojureDllFolder();
			Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
			  Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Source")) + Path.PathSeparator +
			  Path.GetFullPath(Application.dataPath));
		}

		[MenuItem("Arcadia/Initialization/Update Clojure Load Path")]
		public static void SetClojureLoadPath()
		{
			Debug.Log("Setting Load Path...");
			string clojureDllFolder = GetClojureDllFolder();

			Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Compiled", "Editor")) + Path.PathSeparator +
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Source")) + Path.PathSeparator +
				Path.GetFullPath(Application.dataPath) + Path.PathSeparator +
				RT.var("arcadia.compiler", "loadpath-extension-string").invoke() + Path.PathSeparator +
				Path.GetFullPath(VariadicPathCombine(clojureDllFolder, "..", "Libraries")));

			Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
		}
		
		static void LoadSocketREPL ()
		{
			RT.load("arcadia/socket_repl");
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
			RT.load("arcadia/internal/editor_callbacks");
			EditorCallbacks.Initialize();
		}
	}
}