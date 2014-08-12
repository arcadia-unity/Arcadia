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
        "Assets/Clojure/Internal/Plugins",
    };

    static public string pathToAssemblies = "Library/ScriptAssemblies";

    static public void SetupLoadPath() {
        string loadPath = Path.Combine(System.Environment.CurrentDirectory, pathToAssemblies);
        foreach(string path in CompilationRoots) {
            loadPath += ":" + path;
        }

        System.Environment.SetEnvironmentVariable("CLOJURE_LOAD_PATH", loadPath);
        Debug.Log(System.Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH"));
    }

    static public void OnPostprocessAllAssets(
                        String[] importedAssets,
                        String[] deletedAssets,
                        String[] movedAssets,
                        String[] movedFromAssetPaths) {

    // dont need to be doing this every 
    SetupLoadPath();
    
    // only consider imported assets
    foreach(string path in importedAssets) {
        Debug.Log(path);

      // compile only if asset is a .clj file and on a compilation root
      if(path.EndsWith(".clj") && CompilationRoots.Any(r => path.Contains(r))) {
        string root = CompilationRoots.Where(r => path.Contains(r)).Single();
        int rootLength = root.Split(Path.DirectorySeparatorChar).Count();

        string cljNameSpace = String.Join(".", path.Remove(path.Length - 4, 4).Split(Path.DirectorySeparatorChar).Skip(rootLength).ToArray()).Replace("_", "-");

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

            Debug.Log("OK");
        } catch(Exception e) {
            Debug.LogException(e);
        }
      }
    }
  }
}