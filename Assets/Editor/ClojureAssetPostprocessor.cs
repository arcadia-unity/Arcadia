using UnityEngine;
using UnityEditor;
using System;
using System.IO;
using System.Collections;
using System.Linq;
using clojure.lang;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public string pathInAssets = "Plugins/Clojure";

    static public void OnPostprocessAllAssets(
                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {
    foreach(string path in importedAssets) {
      if(path.StartsWith(Path.Combine("Assets", pathInAssets)) && path.EndsWith(".clj")) {
        int rootLength = Path.Combine("Assets", pathInAssets).Split(Path.DirectorySeparatorChar).Count();

        Debug.Log("Path is " + path + "...");
        Debug.Log("Data path is " + Application.dataPath + "...");
        Debug.Log("Compile path is " + Path.Combine(Application.dataPath, pathInAssets) + "...");

        string cljNameSpace = String.Join(".", path.Remove(path.Length - 4, 4).Split(Path.DirectorySeparatorChar).Skip(rootLength).ToArray()).Replace("_", "-");

        Debug.Log("Compiling " + cljNameSpace + "...");

        Var.pushThreadBindings(RT.map(
            Compiler.CompilePathVar, Path.Combine(Application.dataPath, pathInAssets),
            RT.WarnOnReflectionVar, false,
            RT.UncheckedMathVar, false,
            Compiler.CompilerOptionsVar, null
        ));

        Compiler.CompileVar.invoke(Symbol.intern(cljNameSpace));
        AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);

        Debug.Log("OK");

      }
    }
  }
}