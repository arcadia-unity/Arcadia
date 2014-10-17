using UnityEngine;
using UnityEditor;

public class ClojureConfiguration : EditorWindow {
  [SerializeField]
  public static bool AutoCompile = true;

  [MenuItem ("Arcadia/Configuration...")]
  static void Init () {
    ClojureConfiguration window = (ClojureConfiguration)EditorWindow.GetWindow (typeof (ClojureConfiguration));
  }

  void OnGUI () {
    EditorGUILayout.LabelField("This is a temporary hack.");
    EditorGUILayout.LabelField("A beautiful EDN configuration system will take its place.");
    ClojureConfiguration.AutoCompile = EditorGUILayout.Toggle("Auto Compile Clojure Code", ClojureConfiguration.AutoCompile);
  }
}
