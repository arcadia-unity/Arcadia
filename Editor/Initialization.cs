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
      
    [MenuItem ("Arcadia/Initialization/Rerun")]
    public static void Initialize() {
      Debug.Log("Starting Arcadia...");
      
      CheckSettings();
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
        string clojureDllFolder = Path.GetDirectoryName(
          AssetDatabase.FindAssets("Clojure")
            .Select(x => AssetDatabase.GUIDToAssetPath(x))
            .Where(x => x.Contains("Clojure.dll"))
            .Single());
        
        Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
          Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Compiled")) + ":" +
          Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Source")) + ":" +
          Path.GetFullPath(Application.dataPath));
        Debug.Log("Load Path is " + Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
      } catch(InvalidOperationException e) {
        throw new SystemException("Error Loading Arcadia! Arcadia expects exactly one Arcadia folder (a folder with Clojure.dll in it)");
      }
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