using UnityEngine;
using UnityEditor;
using System;
using System.IO;
using System.Collections;
using System.Linq;
using clojure.lang;

class ClojureAssetPostprocessor : AssetPostprocessor {
  static public void OnPostprocessAllAssets(
                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {
    foreach(string path in importedAssets) {
      if(path.EndsWith("Unity.clj")) {

        string cljNameSpace = path.Split(Path.DirectorySeparatorChar).Last().Split(new char[]{'.'}).First();

        Debug.Log("Compiling " + cljNameSpace + "...");

        Var.pushThreadBindings(RT.map(
            Compiler.CompilePathVar, "/Users/nasser/Scratch/sexant/Assets/Plugins/Clojure/",
            // Compiler.CompilePathVar, "/Users/nasser/Scratch/sexant/Assets/",
            RT.WarnOnReflectionVar, false,
            RT.UncheckedMathVar, false,
            Compiler.CompilerOptionsVar, null
        ));

        Compiler.CompileVar.invoke(Symbol.intern(cljNameSpace));

        Debug.Log("OK");
      }
    }
  }
}