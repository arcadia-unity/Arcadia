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
    RT.load("unity/config");
    RT.var("unity.config", "update-from-file!").invoke(configFilePath);
  }
  
  static bool updatedFromFile = false;
  public static bool UpdatedFromFile { get { return updatedFromFile; } }
  
  static string configFilePath = "Assets/Clojure/configure.edn";
  
  static ClojureConfigurationObject _clojureConfigurationObject = new ClojureConfigurationObject();
  
  public static T GetValue<T>(Keyword key) {
    UpdateFromFile();
    return (T)RT.var("unity.config", "value").invoke(key);
  }
  
  public static T GetValue<T>(params Keyword[] keys) {
    UpdateFromFile();
    return (T)RT.var("unity.config", "value-in").invoke(PersistentVector.create(keys));
  }
  
  public static T GetValue<T>(string key) {
    return GetValue<T>(Keyword.intern(null, key));
  }
  
  public static T GetValue<T>(params string[] keys) {
    return GetValue<T>((Keyword[])keys.Select(k => Keyword.intern(null, k)));
  }

  [MenuItem ("Clojure/Test...")]
  public static void Test () {
    GetValue<bool>("verbose");
  }
    
  [MenuItem ("Clojure/Configuration...")]
  public static void Init () {
    Selection.activeObject = _clojureConfigurationObject;
  }

  public override void OnInspectorGUI () {
    configFilePath = EditorGUILayout.TextField("Config File Path", configFilePath);
    RT.var("unity.config", "render-gui").invoke(configFilePath);
  }
  
  
}

public class ClojureConfigurationObject : ScriptableObject {}
