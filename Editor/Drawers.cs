using System;
using System.Linq;
using System.Collections;
using UnityEditor;
using UnityEngine;
using clojure.lang;

[CustomEditor(typeof(ArcadiaBehaviour), true)]
public class ArcadiaBehaviourEditor : Editor {  
  public static IFn requireFn;
  public static IFn titleCase;
  public static Symbol editorInteropSymbol;
  
  static ArcadiaBehaviourEditor() {
    requireFn = RT.var("clojure.core", "require");
  }
  
  int selectedVar = 0;
  void PopupInspector() {
    requireFn.invoke(Symbol.intern("arcadia.internal.editor-interop"));
    Namespace[] namespaces = (Namespace[])RT.var("arcadia.internal.editor-interop", "all-user-namespaces").invoke();
    string[] fullyQualifiedVars = namespaces.
      SelectMany(ns => ns.getMappings().
              Select((IMapEntry me) => me.val()).
              Where(v => v.GetType() == typeof(Var) &&
                         ((Var)v).Namespace == ns).
              Select(v => v.ToString().Substring(2))).
      ToArray();
    EditorGUILayout.Space();
    selectedVar = EditorGUILayout.Popup("Function", selectedVar, fullyQualifiedVars);
    
    ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
    ab.serializedVar = fullyQualifiedVars[selectedVar];
    ab.OnAfterDeserialize();
  }
  
  void TextInspector() {
    EditorGUILayout.Space();
    ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
    ab.serializedVar = EditorGUILayout.TextField("Function", ab.serializedVar);
    ab.OnAfterDeserialize();
  }
  
  public override void OnInspectorGUI () {
    var inspectorConfig = ClojureConfiguration.Get("editor", "hooks-inspector");
    
    TextInspector();
    /*
    if(inspectorConfig == Keyword.intern(null, "drop-down")) {
      PopupInspector();
    } else if(inspectorConfig == Keyword.intern(null, "text")) {
      TextInspector();
    } else {
      EditorGUILayout.HelpBox("Invalid value for :editor/hooks-inspector in configuration file." +
                              "Expected :text or :drop-down, got " + inspectorConfig.ToString() +
                              ". Showing text inspector.", MessageType.Warning);
      TextInspector();
    }
    */
  }
}

[CustomEditor(typeof(ArcadiaState), true)]
public class ArcadiaStateEditor : Editor {
  static ArcadiaStateEditor() {
    RT.load("arcadia/core");
  }
  
  public override void OnInspectorGUI () {
    ArcadiaBehaviourEditor.requireFn.invoke(Symbol.intern("arcadia.internal.editor-interop"));
    ArcadiaState stateComponent = (ArcadiaState)target;
    RT.var("arcadia.internal.editor-interop", "state-inspector!").invoke(stateComponent.state);
  }
}