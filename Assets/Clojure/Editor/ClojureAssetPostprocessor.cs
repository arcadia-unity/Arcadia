using UnityEngine;
using UnityEditor;
using System;
using System.IO;
using System.Collections;
using System.Linq;
using clojure.lang;

class ClojureAssetPostprocessor : AssetPostprocessor {
    static public string[] CompilationRoots = new [] {
        "Assets/Clojure/Scripts",
        "Assets/Clojure/Libraries",
        "Assets/Clojure/Internal",
    };

    static public string pathToAssemblies = "Assets/Clojure/Compiled";

    static public void SetupLoadPath() {
        string loadPath = Path.Combine(System.Environment.CurrentDirectory, pathToAssemblies);
        foreach(string path in CompilationRoots) {
            loadPath += ":" + path;
        }

        System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", loadPath);
        if(ClojureConfiguration.UpdatedFromFile && ClojureConfiguration.GetValue<bool>("verbose"))
            Debug.Log("CLOJURE_LOAD_PATH: " + System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
    }

    static public void OnPostprocessAllAssets(
                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {

    // if(!ClojureConfiguration.GetValue<bool>("compiler", "automatic")) {
    //     foreach(string path in importedAssets) {
    //         Debug.Log("Ignoring " + path);
    //     }
    //     return;
    // }

    // only consider imported assets
    foreach(string path in importedAssets) {
      // compile only if asset is a .clj file and on a compilation root
      if(path.EndsWith(".clj") && CompilationRoots.Any(r => path.Contains(r))) {
        string root = CompilationRoots.Where(r => path.Contains(r)).Single();
        int rootLength = root.Split(Path.DirectorySeparatorChar).Count();

        string cljNameSpace = String.Join(".", path.Remove(path.Length - 4, 4).Split(Path.DirectorySeparatorChar).Skip(rootLength).ToArray()).Replace("_", "-");

        if(ClojureConfiguration.GetValue<bool>("verbose"))
            Debug.Log("Compiling " + cljNameSpace + "...");

        try {
            Var.pushThreadBindings(RT.map(
                Compiler.CompilePathVar, Path.Combine(System.Environment.CurrentDirectory, pathToAssemblies),
                RT.WarnOnReflectionVar, false,
                RT.UncheckedMathVar, false,
                Compiler.CompilerOptionsVar, null
            ));

            Compiler.CompileVar.invoke(Symbol.intern(cljNameSpace));
            AssetDatabase.Refresh(ImportAssetOptions.ForceUpdate);
            
        } catch(Exception e) {
            Debug.LogException(e);
        }
      }
    }
  }
}