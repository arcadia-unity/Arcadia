using UnityEngine;
using UnityEditor;
using System;
using System.IO;
using System.Collections;
using System.Linq;
using clojure.lang;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public string pathInAssets = "Clojure/Scripts";

    static public void SetupLoadPath() {
        System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", Path.Combine(System.Environment.CurrentDirectory, Path.Combine("Assets", pathInAssets)));
        Debug.Log(System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
    }

    static public void OnPostprocessAllAssets(

                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {

    SetupLoadPath();
        
    foreach(string path in importedAssets.Concat(movedAssets)) {
      if(path.StartsWith(Path.Combine("Assets", pathInAssets)) && path.EndsWith(".clj")) {
        int rootLength = Path.Combine("Assets", pathInAssets).Split(Path.DirectorySeparatorChar).Count();

        string cljNameSpace = String.Join(".", path.Remove(path.Length - 4, 4).Split(Path.DirectorySeparatorChar).Skip(rootLength).ToArray()).Replace("_", "-");

        Debug.Log("Compiling " + cljNameSpace + "...");

        try {
            Var.pushThreadBindings(RT.map(
                Compiler.CompilePathVar, Path.Combine(Application.dataPath, pathInAssets),
                RT.WarnOnReflectionVar, false,
                RT.UncheckedMathVar, false,
                Compiler.CompilerOptionsVar, null
            ));

            Compiler.CompileVar.invoke(Symbol.intern(cljNameSpace));
            AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);

            Debug.Log("OK");
        } catch(Exception e) {
            Debug.LogException(e);
        }
      }
    }
  }
}