using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;
using Arcadia;

public class ClojureConfigurationObject : ScriptableObject {}

[CustomEditor(typeof(ClojureConfigurationObject))]
public class ClojureConfiguration : Editor {
  public static string defaultConfigFilePath = Initialization.VariadicPathCombine("Assets", "Arcadia", "configuration.edn");
  public static string userConfigFilePath = Initialization.VariadicPathCombine("Assets", "configuration.edn");

  static ClojureConfigurationObject _clojureConfigurationObject;

  [MenuItem ("Arcadia/Import Dependencies")]
  public static void ImportDependencies () {
    RT.var("arcadia.config", "deps").invoke();
  }
    
  [MenuItem ("Arcadia/Configuration...")]
  public static void Init () {
    RT.load("arcadia/config");    
    RT.var("arcadia.config", "update!").invoke();
    
    if(_clojureConfigurationObject == null)
      _clojureConfigurationObject = ScriptableObject.CreateInstance<ClojureConfigurationObject>();
      
    Selection.activeObject = _clojureConfigurationObject;
  }

  public override void OnInspectorGUI () {
    userConfigFilePath = EditorGUILayout.TextField("Configuration File", userConfigFilePath);
    RT.var("arcadia.config", "render-gui").invoke();
  } 
}