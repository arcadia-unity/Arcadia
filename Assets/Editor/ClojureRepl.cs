using UnityEngine;
using UnityEditor;
using clojure.lang;

public class ClojureRepl : EditorWindow {
  string myString = "Hello World";
  bool groupEnabled;
  bool myBool = true;
  float myFloat = 1.23f;
  string stringToEdit = "Clojure REPL v0.1 (sexant)\n";
  string input = "";

  [MenuItem ("Window/Clojure REPL")]
  static void Init () {
    ClojureRepl window = (ClojureRepl)EditorWindow.GetWindow (typeof (ClojureRepl));
  }
  
  void OnGUI () {
    GUILayout.TextArea(stringToEdit, 200);

    GUI.SetNextControlName("replInput");
    input = GUILayout.TextField(input, 200);

    if(GUILayout.Button("Run!")) {
      RT.load("clojure.core");
      stringToEdit += RT.var("clojure.core", "load-string").invoke(input) + "\n";
    }
  }
}
