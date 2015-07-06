using System;
using System.IO;
using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia {
  [InitializeOnLoad]
  public class Initialization {
    static Initialization() {
      Initialize();
    }

    public static String GetClojureDllFolder(){
    try {
      return
        Path.GetDirectoryName(
          AssetDatabase.GetAllAssetPaths()
            .Where(s => System.Text.RegularExpressions.Regex.IsMatch(s, ".*/Clojure.dll$")) // for compatibility with 4.3
            .Single());
	}catch(InvalidOperationException e){
       	 throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
	}
    }

    public static void ensureCompiledFolder(){
     string maybeCompiled = Path.GetFullPath(VariadicCombine(GetClojureDllFolder(), "..", "Compiled"));
     if(!Directory.Exists(maybeCompiled)){
	Debug.Log("Creating Compiled");
        Directory.CreateDirectory(maybeCompiled);
     }
    }


    [MenuItem ("Arcadia/Initialization/Rerun")]
    public static void Initialize() {
      Debug.Log("Starting Arcadia...");
      
      CheckSettings();
      ensureCompiledFolder();
      SetClojureLoadPath();
      StartREPL();
      
      Debug.Log("Arcadia Started!");    
    }
    
    public static void CheckSettings() {
      Debug.Log("Checking Unity Settings...");
      if(PlayerSettings.apiCompatibilityLevel != ApiCompatibilityLevel.NET_2_0) {
        Debug.Log("Updating API Compatibility Level");
        PlayerSettings.apiCompatibilityLevel = ApiCompatibilityLevel.NET_2_0;
      }
      
      if(!PlayerSettings.runInBackground) {
        Debug.Log("Updating Run In Background");
        PlayerSettings.runInBackground = true;
      }
    }

    public static void SetClojureLoadPath() {
      try {
        Debug.Log("Setting Load Path...");
        string clojureDllFolder = GetClojureDllFolder();
        
        Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
          Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Compiled")) + ":" +
          Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Source")) + ":" +
          Path.GetFullPath(Application.dataPath) + ":" +
          Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Libraries")));
      } catch(InvalidOperationException e) {
        throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
      }

      //phase 2
      RT.load("arcadia/config");
      RT.var("arcadia.config","update!").invoke();
      String clp = (String) RT.var("arcadia.config", "configured-loadpath").invoke();
      Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", clp);
      Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));

    }
    
    static void StartREPL() {
      ClojureRepl.StartREPL();
    }
    
    // old mono...
    static string VariadicCombine(params string[] paths) {
      string path = "";
      foreach(string p in paths) {
        path = Path.Combine(path, p);
      }
      return path;
    }
  } 
}