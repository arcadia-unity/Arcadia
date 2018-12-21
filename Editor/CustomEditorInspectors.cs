#if NET_4_6
using System;
using System.IO;
using System.Runtime.CompilerServices;
using Arcadia;
using UnityEditor;
using UnityEngine;
using clojure.lang;

[CustomEditor(typeof(ArcadiaBehaviour), true)]
public class ArcadiaBehaviourEditor : Editor
{
	public override void OnInspectorGUI()
	{
		ArcadiaBehaviour ab = (ArcadiaBehaviour) target;
		var ifns = ab.ifnInfos;
		if (ifns.Length == 0)
		{
			EditorGUILayout.LabelField("No functions");
		}
		else
		{
			for (int i = 0; i < ifns.Length; i++)
			{
				var ifn = ifns[i];
				Var v = ifn.fn as Var;
				var boldStyle = new GUIStyle(EditorStyles.label);
				boldStyle.font = EditorStyles.boldFont;
				var redStyle = new GUIStyle(boldStyle);
				redStyle.normal.textColor = Color.red;
				if (v != null)
				{
					if (v.isBound)
					{
						EditorGUILayout.BeginHorizontal();
						EditorGUILayout.PrefixLabel(ifn.key.ToString(), EditorStyles.label, boldStyle);
						EditorGUILayout.LabelField(v.ToString());
						EditorGUILayout.EndHorizontal();
					}
					else
					{
						EditorGUILayout.BeginHorizontal();
						EditorGUILayout.PrefixLabel(ifn.key.ToString(), EditorStyles.label, redStyle);
						EditorGUILayout.LabelField(v.ToString());
						EditorGUILayout.EndHorizontal();						
					}
				}
				else
				{
					EditorGUILayout.BeginHorizontal();
					EditorGUILayout.PrefixLabel(ifn.key.ToString(), EditorStyles.label, boldStyle);
					EditorGUILayout.LabelField(ifn.fn.ToString());
					EditorGUILayout.EndHorizontal();
				}
			}
		}
		
		EditorGUILayout.Space();
	}
}

[CustomEditor(typeof(ArcadiaState), true)]
public class ArcadiaStateEditor : Editor
{
	private static Var pprint;
	
	static ArcadiaStateEditor()
	{
		Arcadia.Util.require("clojure.pprint");
		pprint = RT.var("clojure.pprint", "pprint");
	}

	class InspectorCache
	{
		public int Length;
		public bool[] Foldouts;
		public string[] PrintedKeys;
		public string[] PrintedValues;
		public object[] CachedKeys;
		public object[] CachedValues;

		public void UpdateFrom(ArcadiaState state)
		{
			if (!state.fullyInitialized)
				state.Initialize();
			var kvs = state.state.KeyVals();

			Length = kvs.Length;
			if (Foldouts == null || Foldouts.Length != Length)
			{
				Foldouts = new bool[Length];
				for (var i = 0; i < Length; i++)
					Foldouts[i] = true;
			}

			PrintedValues = new string[Length];
			PrintedKeys = new string[Length];
			CachedKeys = new object[Length];
			CachedValues = new object[Length];
		
			for (var i = 0; i < Length; i++)
			{
				var sw = new StringWriter();
				pprint.invoke(kvs[i].key, sw);
				PrintedKeys[i] = sw.ToString();
				sw.GetStringBuilder().Clear();
				pprint.invoke(kvs[i].val, sw);
				PrintedValues[i] = sw.ToString();
				CachedKeys[i] = kvs[i].key;
				CachedValues[i] = kvs[i].val;
			}
		}
	}

	private static ConditionalWeakTable<ArcadiaState, InspectorCache> _cache =
		new ConditionalWeakTable<ArcadiaState, InspectorCache>();
	
	private ArcadiaState state;
	private InspectorCache cache;

	private void MaybeRefresh()
	{
		var kvs = state.state.KeyVals();
		if (kvs.Length != cache.Length)
		{
			Refresh();
			return;
		}

		for (int i = 0; i < cache.Length; i++)
		{
			JumpMap.KeyVal kv;
			if (state.state.dict.TryGetValue(cache.CachedKeys[i], out kv))
			{
				if (!ReferenceEquals(cache.CachedValues[i], kv.val))
				{
					Refresh();
					return;
				}
			}
			else
			{
				Refresh();
				return;
			}
		}
	}

	private void Refresh()
	{
		cache = _cache.GetValue(state, _ => new InspectorCache());
		cache.UpdateFrom(state);
		EditorUtility.SetDirty(target);
	}

	private void OnEnable()
	{
		state = target as ArcadiaState;
		if(state == null)
			throw new Exception(String.Format("target of ArcadiaState inspector has type {0}", target.GetType()));
		
		Refresh();
	}

	public override void OnInspectorGUI()
	{
		MaybeRefresh();
		for (int i = 0; i < cache.PrintedKeys.Length; i++)
		{
			var keyStyle = new GUIStyle(EditorStyles.foldout);
			var valStyle = new GUIStyle(EditorStyles.label);
			keyStyle.font = EditorStyles.boldFont;
			cache.Foldouts[i] = EditorGUILayout.Foldout(cache.Foldouts[i], cache.PrintedKeys[i], keyStyle);
			if (cache.Foldouts[i])
			{
				GUILayout.Label(cache.PrintedValues[i], valStyle);
			}
		}
			
		if (GUILayout.Button("Refresh"))
		{
			Refresh();
		}
	}	
}
#endif