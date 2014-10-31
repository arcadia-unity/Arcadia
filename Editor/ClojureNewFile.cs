using System;
using System.Linq;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

using UnityEngine;
using UnityEditor;
using UnityEditor.ProjectWindowCallback;
using clojure.lang;

public class ClojureNewFile : EditorWindow {
  // TODO support nested folders, hyphen/underscores
  // TODO read file paths from config
  [MenuItem ("Assets/Create/Clojure Component", false, 90)]
  [MenuItem ("Arcadia/New Component", false, 90)]
  public static void NewComponent () {
    var DoCreateScriptAsset = Type.GetType("UnityEditor.ProjectWindowCallback.DoCreateScriptAsset, UnityEditor");
    
    ProjectWindowUtil.StartNameEditingIfProjectWindowExists(0,
      ScriptableObject.CreateInstance(DoCreateScriptAsset) as UnityEditor.ProjectWindowCallback.EndNameEditAction,
      "Assets/Clojure/Scripts/new-component.clj",
      null,
      "Assets/Clojure/Editor/new-component-template.clj.txt");
  }
  
  [MenuItem ("Arcadia/New File", false, 91)]
  public static void NewFile () {
    var DoCreateScriptAsset = Type.GetType("UnityEditor.ProjectWindowCallback.DoCreateScriptAsset, UnityEditor");
    
    ProjectWindowUtil.StartNameEditingIfProjectWindowExists(0,
      ScriptableObject.CreateInstance(DoCreateScriptAsset) as UnityEditor.ProjectWindowCallback.EndNameEditAction,
      "Assets/Clojure/Scripts/new-file.clj",
      null,
      "Assets/Clojure/Editor/new-file-template.clj.txt");
  }
}