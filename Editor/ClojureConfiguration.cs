using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;

[CustomEditor(typeof(ClojureConfigurationObject))]
public class ClojureConfiguration : Editor {
  static void UpdateFromFile() {
    if(updatedFromFile)
      return;
      
    updatedFromFile = true;
    ClojureAssetPostprocessor.SetupLoadPath();
    RT.load("arcadia/config");
    RT.var("arcadia.config", "update-from-file!").invoke(configFilePath);
  }
  
  static bool updatedFromFile = false;
  public static bool UpdatedFromFile { get { return updatedFromFile; } }
  
  static string configFilePath = "Assets/Arcadia/configure.edn";
  
  static ClojureConfigurationObject _clojureConfigurationObject = new ClojureConfigurationObject();
    
  [MenuItem ("Arcadia/Configuration...")]
  public static void Init () {
    Selection.activeObject = _clojureConfigurationObject;
  }

  public override void OnInspectorGUI () {
    configFilePath = EditorGUILayout.TextField("Config File Path", configFilePath);
    RT.var("arcadia.config", "render-gui").invoke(configFilePath);
  }
  
  
}

public class ClojureConfigurationObject : ScriptableObject {}