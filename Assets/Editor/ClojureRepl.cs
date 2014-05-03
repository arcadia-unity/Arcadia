using UnityEngine;
using UnityEditor;
using clojure.lang;
using System.Collections.Generic;

public class ClojureRepl : EditorWindow {
  string output = "Clojure REPL v0.1 (sexant)\n";
  string input = "";
  List<string> history = new List<string>();

  [MenuItem ("Window/Clojure REPL")]
  static void Init () {
    ClojureRepl window = (ClojureRepl)EditorWindow.GetWindow (typeof (ClojureRepl));
  }
  
  void OnGUI () {
    GUILayout.TextArea(output, 200);
    input = GUILayout.TextField(input, 200);

    Event e = Event.current;
    if (e.type == EventType.KeyDown && e.character == '\n') {
      
    } else if (e.type == EventType.KeyDown && e.character == '\n') {
      RT.load("unityRepl");
      output += input + "\n ==> " + RT.var("unityRepl", "repl-eval-string").invoke(input) + "\n";
      input = "";
    }
  }
}
