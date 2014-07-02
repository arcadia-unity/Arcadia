using UnityEngine;
using UnityEditor;
using clojure.lang;
using System.Collections.Generic;
using System.Threading;

public class AsyncReplWindow : EditorWindow {
  string output = "Clojure Async REPL v0.1 (sexant)\n";
  Queue<string> incomingLines = new Queue<string>();
  AsynchronousSocketListener listener;
  Thread thread;

  [MenuItem ("Window/Clojure Async REPL")]
  static void Init () {
    AsyncReplWindow window = (AsyncReplWindow)EditorWindow.GetWindow (typeof (AsyncReplWindow));
    window.StartListening();
  }

  public void StartListening() {
    RT.load("unityRepl");
    listener = new AsynchronousSocketListener();
    listener.OnGetData += GetData;
    thread = new Thread(() => listener.StartListening());
    thread.Start();
  }

  void GetData(string code, int length, StateObject state) {
    incomingLines.Enqueue(code);
  }

  void OnGUI () {
    GUILayout.TextArea(output, 500);
  }

  void Update() {
    while(incomingLines.Count > 0) {
      string line = incomingLines.Dequeue();
      var result = RT.var("unityRepl", "repl-eval-string").invoke(line);
      Debug.Log(result);
      if(result != null)
        listener.Send(listener.state.workSocket, result.ToString() + "\x04");
      else
        listener.Send(listener.state.workSocket, "nil\x04");

      output += line + "\n";      
    }
  }
}
