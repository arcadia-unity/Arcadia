using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
using System.IO;

class ClojureAssetPostprocessor : AssetPostprocessor {
    public static FileSystemWatcher watcher;
    static public void StartWatchingFiles() {
      RT.load("arcadia/compiler");
      
      watcher = new FileSystemWatcher();
      watcher.Path = Path.GetFullPath("Assets");
      Debug.Log("Watching " + watcher.Path);
      watcher.IncludeSubdirectories = true;
      watcher.NotifyFilter = NotifyFilters.LastAccess | NotifyFilters.LastWrite | NotifyFilters.FileName | NotifyFilters.DirectoryName;
      watcher.Filter = "*.clj";
      watcher.Changed += (object source, FileSystemEventArgs e) => {
        Debug.Log("Changed " + e.Name);
        Debug.Log("Changed " + e.FullPath);
        RT.var("arcadia.compiler", "import-asset").invoke(e.Name);
      };
      watcher.EnableRaisingEvents = true;
    }
  
    static public void OnPostprocessAllAssets(String[] importedAssets, String[] deletedAssets, String[] movedAssets, String[] movedFromAssetPaths) {
        RT.load("arcadia/compiler");
        RT.var("arcadia.compiler", "import-assets").invoke(importedAssets);
        // TODO support deleting and moving assets
        // RT.var("arcadia.compiler", "delete-assets").invoke(deletedAssets);
        // RT.var("arcadia.compiler", "move-assets").invoke(movedAssets);
        // RT.var("arcadia.compiler", "move-assets").invoke(movedAssets);
    }
    
      [MenuItem ("Arcadia/Compiler/Reload Assets")]
      public static void ReloadAssets () {
        AssetDatabase.Refresh();
      }
      
      [MenuItem ("Arcadia/Compiler/Force Reload Assets")]
      public static void ForceReloadAssets () {
        AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);
      }
}