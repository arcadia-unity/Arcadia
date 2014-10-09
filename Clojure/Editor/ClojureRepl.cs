using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
using System.Net;
using System.Text;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Threading;

[InitializeOnLoad]
public class ClojureRepl : EditorWindow {
  static ClojureRepl() {
    // TODO read from config
    ClojureAssetPostprocessor.SetupLoadPath();
    ClojureRepl.StartREPL();
  }

  [MenuItem ("Clojure/REPL/Window...")]
  public static void Init () {
    ClojureRepl window = (ClojureRepl)EditorWindow.GetWindow (typeof (ClojureRepl));
  }

  public static void Update() {
    RT.var("unity.repl", "eval-queue").invoke();
  }

  [MenuItem ("Clojure/REPL/Start %#r")]
  public static void StartREPL () {
    RT.load("unity/repl");
    RT.var("unity.repl", "start-server").invoke(11211);
    EditorApplication.update += ClojureRepl.Update;
  }

  [MenuItem ("Clojure/REPL/Stop &#r")]
  public static void StopREPL () {
    RT.var("unity.repl", "stop-server").invoke();
    EditorApplication.update -= ClojureRepl.Update;
  }

  void OnGUI () {
    if(RT.booleanCast(RT.var("unity.repl", "server-running").deref())) {
      GUI.color = Color.red;
      if(GUILayout.Button("Stop REPL")) {
        ClojureRepl.StopREPL();
      }

      GUILayout.Label("REPL is listening");

    } else {
      GUI.color = Color.green;
      if(GUILayout.Button("Start REPL")) {
        ClojureRepl.StartREPL();
      }

      GUILayout.Label("REPL is not running");
    }
  }
}