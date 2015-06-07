using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;

public class ClojureConfigurationObject : ScriptableObject {}

[CustomEditor(typeof(ClojureConfigurationObject))]
public class ClojureConfiguration : Editor {
  public static string configFilePath = "Assets/Arcadia/configure.edn";  
  
  public static string userConfigFilePath = "Assets/ArcadiaConfig/configure.edn";  

  static ClojureConfigurationObject _clojureConfigurationObject;
    
  [MenuItem ("Arcadia/Configuration...")]
  public static void Init () {
    if(_clojureConfigurationObject == null)
      _clojureConfigurationObject = ScriptableObject.CreateInstance<ClojureConfigurationObject>();
      
    RT.var("arcadia.config", "update!").invoke();
    Selection.activeObject = _clojureConfigurationObject;
  }

  public override void OnInspectorGUI () {
    configFilePath = EditorGUILayout.TextField("Config File Path", configFilePath);
    RT.var("arcadia.config", "render-gui").invoke();
  } 
}