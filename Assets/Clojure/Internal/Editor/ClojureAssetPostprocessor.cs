using UnityEngine;
using UnityEditor;
using System;
using System.IO;
using System.Collections;
using System.Linq;
using clojure.lang;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public string pathToScripts = "Clojure/Scripts";
    static public string pathToLibraries = "Clojure/Libraries";

    static public void SetupLoadPath() {
        System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH",
            Path.Combine(System.Environment.CurrentDirectory, Path.Combine("Assets", pathToScripts)) + ":" + 
            Path.Combine(System.Environment.CurrentDirectory, Path.Combine("Assets", pathToLibraries)));
        Debug.Log(System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
    }

    static public void OnPostprocessAllAssets(

                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {

    SetupLoadPath();
        
    foreach(string path in importedAssets.Concat(movedAssets)) {
      if(path.StartsWith(Path.Combine("Assets", pathToScripts)) && path.EndsWith(".clj")) {
        int rootLength = Path.Combine("Assets", pathToScripts).Split(Path.DirectorySeparatorChar).Count();

        string cljNameSpace = String.Join(".", path.Remove(path.Length - 4, 4).Split(Path.DirectorySeparatorChar).Skip(rootLength).ToArray()).Replace("_", "-");

        Debug.Log("Compiling " + cljNameSpace + "...");

        try {
            Var.pushThreadBindings(RT.map(
                Compiler.CompilePathVar, Path.Combine(Application.dataPath, pathToScripts),
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