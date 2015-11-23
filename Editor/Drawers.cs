using System;
using System.Collections;
using UnityEditor;
using UnityEngine;
using clojure.lang;


// [CustomEditor(typeof(ArcadiaComponent), true)]
// public class ArcadiaComponentEditor : Editor {  
//   public override void OnInspectorGUI () {
//     EditorGUILayout.LabelField("ArcadiaComponentEditor");
//     Debug.Log(target);
//   }
// }

[CustomEditor(typeof(ArcadiaState), true)]
public class ArcadiaStateEditor : Editor {
  static ArcadiaStateEditor() {
    RT.load("clojure/pprint");
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
    if(val is string) {
      return EditorGUILayout.TextField(key.ToString(), (string)val);
      
    } else if(val is bool) {
      return EditorGUILayout.Toggle(key.ToString(), (bool)val);
      
    } else if(val is int) {
      return EditorGUILayout.IntField(key.ToString(), (int)val);
      
    } else if(val is long) {
      return EditorGUILayout.IntField(key.ToString(), (int)(long)val);
      
    } else if(val is Vector2) {
      return EditorGUILayout.Vector2Field(key.ToString(), (Vector2)val);
      
    } else if(val is Vector3) {
      return EditorGUILayout.Vector3Field(key.ToString(), (Vector3)val);
      
    } else if(val is Vector4) {
      return EditorGUILayout.Vector4Field(key.ToString(), (Vector4)val);
      
    } else if(val is float) {
      return EditorGUILayout.FloatField(key.ToString(), (float)val);
      
    } else if(val is double) {
      return EditorGUILayout.FloatField(key.ToString(), (float)(double)val);
      
    } else if(val is Symbol) {
      return Symbol.intern(EditorGUILayout.TextField(key.ToString(), val.ToString()));
      
    } else if(val is Keyword) {
      return Keyword.intern(EditorGUILayout.TextField(key.ToString(), ((Keyword)val).ToString().Substring(1)));
      
    } else if(val is IPersistentMap) {
      IPersistentMap map = val as IPersistentMap;
      
      EditorGUILayout.LabelField(key.ToString(), "{");
      
      EditorGUI.indentLevel++;
      foreach(var entry in map) {
        map = map.assoc(entry.key(), DrawStaticWidget(entry.key(), entry.val()));
      }      
      // EditorGUILayout.LabelField("", "}");
      EditorGUI.indentLevel--;
      return map;
      
    } else if(val is IPersistentVector) {
      IPersistentVector vector = val as IPersistentVector; 
      EditorGUILayout.LabelField(key.ToString(), "[");
      EditorGUI.indentLevel++;
      for(int i=0; i<vector.count(); i++) {
        vector = vector.assocN(i, DrawStaticWidget(i.ToString(), vector.nth(i)));
      }      
      // EditorGUILayout.LabelField("", "]");
      EditorGUI.indentLevel--;
      return vector;
      
    } else {
      EditorGUILayout.LabelField(key.ToString(), "val.GetType().ToString()");
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
    
  int tab = 0;
  
  public override void OnInspectorGUI () {
    tab = Tabs(new [] {"Static", "Dynamic", "Raw"}, tab);
    switch(tab) {
      case 0:
      ((ArcadiaState)target).state = DrawStaticWidget("", ((ArcadiaState)target).state);
      break;
      
      case 1:
      ((ArcadiaState)target).state = DrawDynamicWidget("", ((ArcadiaState)target).state);
      break;
      
      case 2:
      ((ArcadiaState)target).state = DrawRawWidget(((ArcadiaState)target).state);
      break;
    }
  }
}