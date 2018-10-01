#if NET_4_6
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
			if (requireVar == null) {
				Invoke(RT.var("clojure.core", "require"),
					   Symbol.intern("arcadia.internal.namespace"));
				requireVar = RT.var("arcadia.internal.namespace", "quickquire");
			}
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

		// ==================================================================
		// Arrays

		// Could use linq for this stuff, but sometimes there's a virtue
		// to explicitness and nonmagic

		public static T[] ArrayAppend<T> (T[] arr, T x)
		{
			T[] arr2 = new T[arr.Length + 1];
			arr.CopyTo(arr2, 0);
			arr2[arr2.Length - 1] = x;
			return arr2;
		}

		public static T[] ArrayPrepend<T> (T[] arr, T x)
		{
			T[] arr2 = new T[arr.Length + 1];
			if (arr2.Length > 1) {
				Array.Copy(arr, 0, arr2, 1, arr.Length);
			}
			arr2[0] = x;
			return arr2;
		}

		public static T[] ArrayConcat<T> (T[] arr1, T[] arr2)
		{
			T[] arr3 = new T[arr1.Length + arr2.Length];
			Array.Copy(arr1, 0, arr3, 0, arr1.Length);
			Array.Copy(arr2, 0, arr3, arr1.Length, arr2.Length);
			return arr3;
		}

		// test this
		public static T[] ArrayRemove<T> (T[] arr, int inx)
		{
			if (inx < 0 || arr.Length < inx)
				throw new IndexOutOfRangeException();
			T[] arr2 = new T[arr.Length - 1];
			if (arr2.Length == 0)
				return arr2;
			Array.Copy(arr, 0, arr2, 0, inx);
			Array.Copy(arr, inx + 1, arr2, inx, arr.Length - inx - 1);
			return arr2;
		}

		// mutating ops

		public static void WindowShift<T> (T[] arr, int windowStart, int windowEnd, int shiftTo)
		{
			Array.Copy(arr, windowStart, arr, shiftTo, windowEnd - windowStart);
		}

		// move an item to another place and shift everything else to fill in
		public static void Reposition<T> (T[] arr, int sourceInx, int targInx)
		{
			var x = arr[sourceInx];
			if (sourceInx == targInx) {
				return;
			} else if (sourceInx < targInx) {
				WindowShift(arr, sourceInx + 1, targInx, sourceInx);
			} else {
				WindowShift(arr, targInx, sourceInx, targInx + 1);
			}
			arr[targInx] = x;
		}

		// ==================================================================
		// Persistent maps

		public static IPersistentMap Zipmap (object[] ks, object[] vs)
		{
			var len = Mathf.Min(ks.Length, vs.Length) * 2;
			object[] kvs = new object[len];
			for (int i = 0; i < len; i += 2) {
				kvs[i] = ks[i / 2];
				kvs[i + 1] = vs[i / 2];
			}
			return PersistentHashMap.create(kvs);
		}

		// ==================================================================
		// nil

		public static object TrueNil (object obj)
		{
			UnityEngine.Object obj2 = obj as UnityEngine.Object;
			if (obj2 == null) {
				return null;
			}
			return obj;
		}
	}
}
#endif