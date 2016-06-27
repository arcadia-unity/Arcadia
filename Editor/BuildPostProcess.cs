using UnityEngine;
using UnityEditor;
using UnityEditor.Callbacks;
using System.IO;
using clojure.lang;

namespace Arcadia {
  public class BuildPostProcess {
    [PostProcessBuildAttribute(1)]
    public static void OnPostprocessBuild(BuildTarget target, string pathToBuiltProject) {
     if(target == BuildTarget.StandaloneOSXUniversal || target == BuildTarget.StandaloneOSXIntel || target == BuildTarget.StandaloneOSXIntel64) {
       var targetFolder = pathToBuiltProject + "/Contents/Resources/Data/Managed";
       Debug.Log("Exporting Arcadia to " + targetFolder);
       RT.load("arcadia/internal/editor_interop");
       RT.load("arcadia/compiler");
       var userNameSpaces = RT.var("arcadia.internal.editor-interop", "all-user-namespaces-symbols").invoke();
       RT.var("arcadia.compiler", "aot-namespaces").invoke(targetFolder, userNameSpaces);
       RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("arcadia.core"));
       RT.var("arcadia.compiler", "aot-namespace").invoke(targetFolder, Symbol.intern("clojure.core"));
       File.Copy(RT.FindFile("data_readers.clj").FullName, targetFolder + "/data_readers.clj");
       
       } else {
         EditorUtility.DisplayDialog(
           "Unsupported Export Target",
           "Arcadia does not yet support export to the target " + target,
           "OK");
       }
     }
   }
 }