using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
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

    static string FindClojureDll() {
      var paths = AssetDatabase.GetAllAssetPaths()
        .Where(p => Regex.IsMatch(p, @".*/Clojure\.dll$"))
        .Select(p => Path.GetDirectoryName(p));

      if (paths.Count() > 1) {
        string error = "Found multiple directories containing Clojure.dll:\n";

        foreach (var path in paths)
        {
          error += String.Format(" - \"{0}\"\n", path);
        }

        throw new Exception(error);
      }
      else if (!paths.Any()) {
        throw new Exception("Found no directories containing Clojure.dll!");
      }

      Debug.Log(String.Format("Found Clojure.dll in \"{0}\"", paths.First()));
      return paths.First();
    }

    static string PathSeperator() {
      if (SystemInfo.operatingSystem.ToLower().Contains("windows")) {
        return ";";
      }
      else {
        return ":";
      }
    }

    public static void SetClojureLoadPath()
    {
      Debug.Log("Setting Load Path...");

      string clojureDllFolder = FindClojureDll();
      string sep = PathSeperator();

      Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
        Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Compiled")) + sep +
        Path.GetFullPath(VariadicCombine(clojureDllFolder, "..", "Source")) + sep +
        Path.GetFullPath(Application.dataPath));
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