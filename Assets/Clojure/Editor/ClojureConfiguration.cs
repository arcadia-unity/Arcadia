using UnityEngine;
using UnityEditor;
using clojure.lang;

[InitializeOnLoad]
public class ClojureConfiguration : EditorWindow {
  static ClojureConfiguration() {
    RT.load("unity/config");
  }
  
  [SerializeField]
  public static bool AutoCompile = true;

  [MenuItem ("Clojure/Configuration...")]
  public static void Init () {
    ClojureConfiguration window = (ClojureConfiguration)EditorWindow.GetWindow (typeof (ClojureConfiguration));
  }

  void OnGUI () {
    EditorGUILayout.LabelField("This is a temporary hack.");
    // EditorGUILayout.L abelField("A beautiful EDN configuration system will take its place.");
    // ClojureConfiguration.AutoCompile = EditorGUILayout.Toggle("Auto Compile Clojure Code", ClojureConfiguration.AutoCompile);
    RT.var("unity.config", "render-gui").invoke("Assets/Clojure/configure.edn");
  }
}
