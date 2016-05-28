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
  
  
  // http://forum.unity3d.com/threads/gui-tab-style-controls.89085/
  public static int Tabs(string[] options, int selected) {
    const float DarkGray = 0.4f;
    const float LightGray = 0.9f;
    const float StartSpace = 10;
    
    // GUILayout.Space(StartSpace);
    // buttonStyle.padding.bottom = 8;
    
    GUILayout.BeginHorizontal();
    
    for (int i = 0; i < options.Length; ++i) {
      GUIStyle buttonStyle;
      if(i == 0)
        buttonStyle = EditorStyles.miniButtonLeft;
      else if(i == options.Length - 1)
        buttonStyle = EditorStyles.miniButtonRight;
      else
        buttonStyle = EditorStyles.miniButtonMid;
      
      // buttonStyle.margin.left = 0;
      // buttonStyle.margin.right = -1;
      
      if (GUILayout.Toggle(i == selected, options[i], buttonStyle)) {
        selected = i;
      }
    }
    
    GUILayout.EndHorizontal();
    return selected;
  }
  
  object DrawStaticWidget(object key, object val) {
    string label;
    if(key is Named)
      // honest
      // label = key.ToString();
      // compact
      label = (((Named)key).getNamespace() != null ? "::" : ":") + ((Named)key).getName();
      // title-case
      // label = (string)RT.var("arcadia.internal.editor-interop", "title-case").invoke(key);
    else
      label = key.ToString();
      
    if(val is string) {
      return EditorGUILayout.TextField(label, (string)val);
      
    } else if(val is bool) {
      return EditorGUILayout.Toggle(label, (bool)val);
      
    } else if(val is int) {
      return EditorGUILayout.IntField(label, (int)val);
      
    } else if(val is long) {
      return EditorGUILayout.IntField(label, (int)(long)val);
      
    } else if(val is Vector2) {
      return EditorGUILayout.Vector2Field(label, (Vector2)val);
      
    } else if(val is Vector3) {
      return EditorGUILayout.Vector3Field(label, (Vector3)val);
      
    } else if(val is Vector4) {
      return EditorGUILayout.Vector4Field(label, (Vector4)val);
      
    } else if(val is float) {
      return EditorGUILayout.FloatField(label, (float)val);
      
    } else if(val is double) {
      return EditorGUILayout.FloatField(label, (float)(double)val);
      
    } else if(val is Symbol) {
      return Symbol.intern(EditorGUILayout.TextField(label, val.ToString()));
      
    } else if(val is Keyword) {
      return Keyword.intern(EditorGUILayout.TextField(label, ((Keyword)val).ToString().Substring(1)));
      
    } else if(val is IPersistentMap) {
      IPersistentMap map = val as IPersistentMap;
      
      EditorGUILayout.LabelField(label, "{");
      
      EditorGUI.indentLevel++;
      foreach(var entry in map) {
        map = map.assoc(entry.key(), DrawStaticWidget(entry.key(), entry.val()));
      }      
      // EditorGUILayout.LabelField("", "}");
      EditorGUI.indentLevel--;
      return map;
      
    } else if(val is IPersistentVector) {
      IPersistentVector vector = val as IPersistentVector; 
      EditorGUILayout.LabelField(label, "[");
      EditorGUI.indentLevel++;
      for(int i=0; i<vector.count(); i++) {
        vector = vector.assocN(i, DrawStaticWidget(i.ToString(), vector.nth(i)));
      }      
      // EditorGUILayout.LabelField("", "]");
      EditorGUI.indentLevel--;
      return vector;
      
    } else {
      EditorGUILayout.LabelField(label, val.GetType().ToString());
      return val;
    }
  }
  
  object ReadString(string s) {
    return RT.var("clojure.core", "read-string").invoke(s);
  }
  
  object Gensym() {
    return RT.var("clojure.core", "gensym").invoke();
  }
  
  object Gensym(object o) {
    return RT.var("clojure.core", "gensym").invoke(o);
  }
  
  object LoadString(string s) {
    return RT.var("clojure.core", "load-string").invoke(s);
  }
  
  string PrStr(object o) {
    return (string)RT.var("clojure.core", "pr-str").invoke(o);
  }
  
  object DrawDynamicWidget(object key, object val) {
    if(val is IPersistentMap) {
      IPersistentMap map = val as IPersistentMap;
      
      EditorGUILayout.LabelField(key.ToString(), "{");
      EditorGUI.indentLevel++;
      foreach(var entry in map) {
        object oldKey = entry.key();
        GUILayout.BeginHorizontal();
        object newKey = LoadString(EditorGUILayout.TextField(PrStr(entry.key())));
        object newVal = LoadString(EditorGUILayout.TextField(PrStr(entry.val())));
        map = map.without(oldKey);
        
        if(!GUILayout.Button("X", EditorStyles.miniButton)) {
          map = map.assoc(newKey, newVal);
        }
        
        GUILayout.EndHorizontal();
      }
      if(GUILayout.Button("+", EditorStyles.miniButton)) {
        map = map.assoc("", "");
      }
        
      EditorGUI.indentLevel--;
      return map;
      
    } else if(val is IPersistentVector) {
      IPersistentVector vector = val as IPersistentVector; 
      EditorGUILayout.LabelField(key.ToString(), "[");
      EditorGUI.indentLevel++;
      for(int i=0; i<vector.count(); i++) {
        vector = vector.assocN(i, DrawDynamicWidget(i.ToString(), vector.nth(i)));
      }      
      // EditorGUILayout.LabelField("", "]");
      EditorGUI.indentLevel--;
      return vector;
      
    } else {
      return RT.var("clojure.core", "read-string").invoke(EditorGUILayout.TextField(key.ToString(), (string)RT.var("clojure.core", "pr-str").invoke(val)));
      
    }
  }
  
  static Keyword streamKw = Keyword.intern("stream");
  static Keyword rightMarginKw = Keyword.intern("right-margin");
  static Keyword miserWidthKw = Keyword.intern("miser-width");
  
  object DrawRawWidget(object o) {
    string oString = (string)RT.var("clojure.pprint", "write").invoke(o, streamKw, null, rightMarginKw, 40, miserWidthKw, 40);
    string editorResult = EditorGUILayout.TextArea(oString, GUILayout.ExpandHeight(true), GUILayout.ExpandWidth(true));
    return RT.var("clojure.core", "read-string").invoke(editorResult);
  }
  
  [SerializeField]
  string customEditorVar = "";
  object DrawCustomWidget(object o) {
    customEditorVar = EditorGUILayout.TextField("Var", (string)customEditorVar);
    if(customEditorVar.Length > 0) {
      Symbol sym = Symbol.intern(customEditorVar);
      if(sym.Namespace != null) {
        Symbol nssym = Symbol.intern(sym.Namespace);
        if(nssym != null) {
          RT.var("clojure.core", "require").invoke(nssym);
          Var fn = RT.var(sym.Namespace, sym.Name);
          if(fn.isBound) {
            return fn.invoke(o);
          }
        }
      }
    }
    
    return o;
  }
    
  int tab = 0;
  
  void StartStateGroup() {
    EditorGUILayout.BeginVertical (new GUIStyle (EditorStyles.helpBox));
    Rect rect = GUILayoutUtility.GetRect (20f, 2f);
  }
  
  bool StartStateGroup(string title, bool value) {
    EditorGUILayout.BeginVertical (new GUIStyle (EditorStyles.helpBox));
    Rect rect = GUILayoutUtility.GetRect (20f, 18f);
    rect.x += 3f;
    var style = EditorGUIUtility.GetBuiltinSkin (EditorSkin.Inspector).FindStyle ("IN TitleText");
    return GUI.Toggle (rect, value, title, style);
    // EditorGUILayout.BeginFadeGroup (0.5f);
    
  }
  
  void EndStateGroup() {
    GUILayoutUtility.GetRect (20f, 2f);
    EditorGUILayout.EndVertical ();
  }
  
  PersistentHashMap keyedFoldouts = PersistentHashMap.EMPTY;
  
  public override void OnInspectorGUI () {
    ArcadiaBehaviourEditor.requireFn.invoke(Symbol.intern("arcadia.internal.editor-interop"));
    ArcadiaState stateComponent = (ArcadiaState)target;
    IPersistentMap state = (IPersistentMap)stateComponent.state.deref();
    
    ISeq groupedState = (ISeq)RT.var("arcadia.internal.editor-interop", "grouped-state-map").invoke(state);
    
    do {
      IPersistentVector group = (IPersistentVector)groupedState.first();
      string groupName = (string)group.valAt(0);
      PersistentVector groupBody = (PersistentVector)group.valAt(1);
      
      if(groupName != null) 
        // titlecase
        // StartStateGroup((string)RT.var("arcadia.internal.editor-interop", "title-case").invoke(groupName), true);
        // honest
        StartStateGroup(groupName, true);
      else
        StartStateGroup();
        
      foreach(IPersistentVector kv in groupBody) {
        DrawStaticWidget(kv.valAt(0), kv.valAt(1));
      }
      EndStateGroup();
      groupedState = groupedState.next();
    } while(groupedState != null);

//    foreach(var kv in state) {
//      if(kv.key() == ArcadiaCoreAnonymousKeyword)
//        continue;
//      bool v = StartStateGroup(kv.key().ToString(), (bool)keyedFoldouts.valAt(kv.key(), true));
//      keyedFoldouts = (PersistentHashMap)keyedFoldouts.assoc(kv.key(), v);
//      if(v)
//        foreach(var skv in (IPersistentMap)kv.val()) {
//          DrawStaticWidget(skv.key(), skv.val());
//        }
//      EndStateGroup();
//    }
  }
}