using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public void OnPostprocessAllAssets(String[] importedAssets, String[] deletedAssets, String[] movedAssets, String[] movedFromAssetPaths) {
        RT.load("arcadia/compiler");
        RT.var("arcadia.compiler", "import-assets").invoke(importedAssets);
        // TODO support deleting and moving assets
        // RT.var("arcadia.compiler", "delete-assets").invoke(deletedAssets);
        // RT.var("arcadia.compiler", "move-assets").invoke(movedAssets);
        // RT.var("arcadia.compiler", "move-assets").invoke(movedAssets);
    }
    
      [MenuItem ("Arcadia/Reload Assets")]
      public static void ReloadAssets () {
        AssetDatabase.Refresh();
      }
}