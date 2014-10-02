using UnityEngine;
using UnityEditor;
using clojure.lang;

[InitializeOnLoad]
public class ClojureConfiguration : EditorWindow {
  static ClojureConfiguration() {
    ClojureAssetPostprocessor.SetupLoadPath();
    RT.load("unity/config");
    RT.var("unity.config", "update-from-file!").invoke(configFilePath);
  }
  
  [SerializeField]
  static string configFilePath = "Assets/Clojure/configure.edn";
  
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
    ClojureConfiguration window = (ClojureConfiguration)EditorWindow.GetWindow (typeof (ClojureConfiguration));
  }

  void OnGUI () {
    configFilePath = EditorGUILayout.TextField("Config File Path", configFilePath);
    RT.var("unity.config", "render-gui").invoke(configFilePath);
  }
}
