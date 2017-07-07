using System;
using UnityEngine;
using clojure.lang;
#if UNITY_EDITOR
using UnityEditor;
using UnityEditor.SceneManagement;
#endif

namespace Arcadia
{
	public class Util
	{
		public static void MarkScenesDirty ()
		{
#if UNITY_EDITOR
			if (!Application.isPlaying) {
				EditorSceneManager.MarkAllScenesDirty();
			}
#endif
		}

	    // ==================================================================
		// namespace and Var loading

		public static Var requireVar;

		public static void require (string s)
		{
			if (requireVar == null)
				requireVar = RT.var("clojure.core", "require");
			Invoke(requireVar, Symbol.intern(s));
		}

		public static void getVar (ref Var v, string ns, string name)
		{
			if (v == null)
				v = RT.var(ns, name);				
		}

		// ==================================================================
		// Var invocation

		public static object Invoke (Var v, object a)
		{
			return ((IFn)v.getRawRoot()).invoke(a);
		}

		public static object Invoke (Var v, object a, object b)
		{
			return ((IFn)v.getRawRoot()).invoke(a, b);
		}

		public static object Invoke (Var v, object a, object b, object c)
		{
			return ((IFn)v.getRawRoot()).invoke(a, b, c);
		}


	}
}