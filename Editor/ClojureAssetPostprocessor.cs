using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public void OnPostprocessAllAssets(String[] importedAssets, String[] deletedAssets, String[] movedAssets, String[] movedFromAssetPaths) {
        RT.load("arcadia/compiler");
        RT.var("arcadia.compiler", "process-assets").invoke(importedAssets);        
    }
    
      [MenuItem ("Arcadia/Reload Assets")]
      public static void ReloadAssets () {
        AssetDatabase.Refresh();
      }
}