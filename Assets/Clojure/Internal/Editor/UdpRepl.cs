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
public class UdpReplWindow : EditorWindow {
  static UdpReplWindow() {
    // TODO read from config
    UdpReplWindow.StartREPL();
  }

  [MenuItem ("Clojure/UDP REPL/Window...")]
  public static void Init () {
    UdpReplWindow window = (UdpReplWindow)EditorWindow.GetWindow (typeof (UdpReplWindow));
  }

  public static void Update() {
    RT.var("unity.repl", "eval-queue").invoke();
  }

  [MenuItem ("Clojure/UDP REPL/Start %#r")]
  public static void StartREPL () {
    RT.load("unity.repl");
    RT.var("unity.repl", "start-server").invoke(11211);
    EditorApplication.update += UdpReplWindow.Update;
  }

  [MenuItem ("Clojure/UDP REPL/Stop &#r")]
  public static void StopREPL () {
    RT.var("unity.repl", "stop-server").invoke();
    EditorApplication.update -= UdpReplWindow.Update;
  }

  void OnGUI () {
    if(RT.booleanCast(RT.var("unity.repl", "server-running").deref())) {
      GUI.color = Color.red;
      if(GUILayout.Button("Stop REPL")) {
        UdpReplWindow.StopREPL();
      }

      GUILayout.Label("REPL is listening");

    } else {
      GUI.color = Color.green;
      if(GUILayout.Button("Start REPL")) {
        UdpReplWindow.StartREPL();
      }

      GUILayout.Label("REPL is not running");
    }
  }
}