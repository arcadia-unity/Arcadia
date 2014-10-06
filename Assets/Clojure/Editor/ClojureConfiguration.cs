using UnityEngine;
using UnityEditor;
using clojure.lang;

[InitializeOnLoad]
[CustomEditor(typeof(ClojureConfigurationObject))]
public class ClojureConfiguration : Editor {
  static ClojureConfiguration() {
    ClojureAssetPostprocessor.SetupLoadPath();
    RT.load("unity/config");
    RT.var("unity.config", "update-from-file!").invoke(configFilePath);
  }
  
  [SerializeField]
  static string configFilePath = "Assets/Clojure/configure.edn";
  static ClojureConfigurationObject _clojureConfigurationObject = new ClojureConfigurationObject();
  
  public static T GetValue<T>(Keyword key) {
    // RT.load("unity/config");
    // return (T)((PersistentHashMap)((Atom)RT.var("unity.config", "config").deref()).deref()).valAt(key);
    return default(T);
  }
  
  public static T GetValue<T>(string key) {
    return GetValue<T>(Keyword.intern(null, key));
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