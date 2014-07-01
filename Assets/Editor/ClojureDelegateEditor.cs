using System;
using UnityEngine;
using UnityEditor;
using clojure.lang;
 
[CustomEditor(typeof(ClojureDelegate))] 
public class ClojureDelegateEditor : Editor {
  public void OnEnable() {
    ClojureDelegate t = (ClojureDelegate) target;
    RT.load(t.nameSpace);
  }

  public override void OnInspectorGUI() {
    ClojureDelegate t = (ClojureDelegate) target;

    Var fnVar = RT.var(t.nameSpace, t.prefix + "on-inspector-gui");
    if(fnVar.isBound) {
      bool repaint = (bool)fnVar.invoke((System.Object)t.gameObject);
    }
  }
}