#if NET_4_6
using System;
using System.Linq;
using System.Collections;
using System.Collections.Generic;
using UnityEditor;
using UnityEditorInternal;
using UnityEngine;
using clojure.lang;

[CustomEditor(typeof(ArcadiaBehaviour), true)]
public class ArcadiaBehaviourEditor : Editor
{
	public static IFn requireFn;
	public static IFn intoArrayFn;
	public static IFn allUserFns;
	public static IFn titleCase;
	public static Symbol editorInteropSymbol;

	static ArcadiaBehaviourEditor()
	{
		requireFn = RT.var("clojure.core", "require");
		requireFn.invoke(Symbol.intern("arcadia.internal.editor-interop"));
		intoArrayFn = RT.var("clojure.core", "into-array");
		allUserFns = RT.var("arcadia.internal.editor-interop", "all-user-fns");
	}

	public static ReorderableList rl;

	void OnEnable()
	{
		ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
		//if (rl == null)
		//{
		//	rl = new ReorderableList(ab.fns, typeof(IFn), true, false, true, true);
		//	rl.headerHeight = 4;
		//	rl.onAddDropdownCallback = (buttonRect, list) =>
		//	{
		//		EditorGUI.Popup(buttonRect, 0, ((IList<object>)allUserFns.invoke()).Select(x => x.ToString().Substring(2)).ToArray());
		//	};
		//}
	}

	void PopupInspector()
	{
		EditorGUILayout.Space();
		ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
		rl.DoLayoutList();
	//	if (ab.serializedVar != null)
	//	{
	//		// var, show popup
	//		var loadedNamespaces = (ISeq)allLoadedUserNamespacesFn.invoke();
	//		if (loadedNamespaces.count() == 0)
	//			return;
	//		Namespace[] namespaces = (Namespace[])intoArrayFn.invoke(loadedNamespaces);
	//		var fullyQualifiedVars = namespaces.
	//		SelectMany(ns => ns.getMappings().
	//		  Select((IMapEntry me) => me.val()).
	//		  Where(v => v.GetType() == typeof(Var) &&
	//		   ((Var)v).Namespace == ns).
	//		  Select(v => v.ToString().Substring(2)));

	//		string[] blank = new string[] { "" };
	//		string[] popUpItems = blank.Concat(fullyQualifiedVars).ToArray();

	//		int selectedVar = Array.IndexOf(popUpItems, ab.serializedVar);
	//		if (selectedVar < 0) selectedVar = 0;
	//		selectedVar = EditorGUILayout.Popup("Function", selectedVar, popUpItems);

	//		ab.serializedVar = popUpItems[selectedVar];
	//		ab.OnAfterDeserialize();
	//	}
	//	else {
	//		EditorGUILayout.LabelField("Function", ab.fn == null ? "nil" : ab.fn.ToString());

	//	}
	}

	void TextInspector()
	{
		//EditorGUILayout.Space();
		//ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
		//ab.serializedVar = EditorGUILayout.TextField("Function", ab.serializedVar);
		//ab.OnAfterDeserialize();
	}

	public override void OnInspectorGUI()
	{
		// var inspectorConfig = ClojureConfiguration.Get("editor", "hooks-inspector");

		// EditorGUILayout.LabelField("Disabled For Now, use the REPL");

		ArcadiaBehaviour ab = (ArcadiaBehaviour)target;
		//var fns = ab.fns;
		//var keys = ab.keys;
		var ifns = ab.ifnInfos;
		if(ifns.Length == 0)
		{
			EditorGUILayout.LabelField("No functions");
		}
		else
		{
			for (int i = 0; i < ifns.Length; i++)
			{
				var ifn = ifns[i];				
				Var v = ifn.fn as Var;
				if (v != null && v.isBound)
				{
					EditorGUILayout.LabelField(ifn.key.ToString() + ": " + v.ToString());
				} else {
					GUIStyle style = new GUIStyle(GUI.skin.label);
					style.normal.textColor = Color.red;
					EditorGUILayout.LabelField(ifn.key.ToString() + ": " + ifn.fn.ToString(), style);
				}
				
			}
		}
		
		// PopupInspector();
		/*
		if(inspectorConfig == Keyword.intern(null, "popup")) {
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
public class ArcadiaStateEditor : Editor
{
	static ArcadiaStateEditor()
	{
        Arcadia.Util.require("arcadia.core");
	}

	public static Var OnInspectorGUIVar;
	public static bool ownVarsInitialized = false;

	public static void RequireOwnVars ()
	{
		if (ownVarsInitialized)
			return;
		string ns = "arcadia.internal.editor-interop";
		Arcadia.Util.require(ns);
		Arcadia.Util.getVar(ref OnInspectorGUIVar, ns, "state-inspector!");
		ownVarsInitialized = true;
	}
	
	public override void OnInspectorGUI()
	{
		RequireOwnVars();
		Arcadia.Util.Invoke(OnInspectorGUIVar, target);
	}
}
#endif