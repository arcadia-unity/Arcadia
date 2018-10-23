#if NET_4_6
using System;
using UnityEngine;
using clojure.lang;
#if UNITY_EDITOR
using UnityEditor;
using System.Collections.Generic;
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

		public static IPersistentMap DictionaryToMap <T1,T2>(Dictionary<T1,T2> dict)
		{
			ITransientMap bldg =  (ITransientMap)PersistentHashMap.EMPTY.asTransient();
			foreach (var kv in dict) {
				bldg = bldg.assoc(kv.Key, kv.Value);
			}
			return bldg.persistent();
		}

		// TODO replace when better interface for fast PersistentMap iteration
		class MapToDictionaryRFn<T1, T2> : AFn
		{
			Dictionary<T1, T2> dict;

			public MapToDictionaryRFn (Dictionary<T1, T2> dict_)
			{
				dict = dict_;
			}

			public override object invoke (object arg, object k, object v)
			{
				dict.Add((T1)k, (T2)v);
				return arg;
			}
		}

		public static Dictionary<T1, T2> MapToDictionary<T1, T2> (IPersistentMap m)
		{
			return MapToDictionary<T1, T2>(m, new Dictionary<T1, T2>());
		}

		public static Dictionary<T1, T2> MapToDictionary<T1, T2> (IPersistentMap m, Dictionary<T1, T2> dict)
		{			
			clojure.lang.IKVReduce m2 = m as clojure.lang.IKVReduce;
			if (m2 != null) {
				m2.kvreduce(new MapToDictionaryRFn<T1, T2>(dict), null);
			} else {
				foreach (var e in m) {
					dict.Add((T1)e.key(), (T2)e.val());
				}
			}
			return dict;
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

		// ==================================================================
		// Equals (for sanity)

		public static bool DoesEqual (object x, object y)
		{
			return x == y;
		}

		public static bool ObjectDoesEqual (GameObject x, GameObject y)
		{
			return x == y;
		}

		// ==================================================================
		// String

		public static string TypeNameToNamespaceName (string typeName)
		{
			var inx = typeName.LastIndexOf('.');
			if (inx != -1) {
				return typeName.Substring(0, inx).Replace('_', '-');
			} 
			throw new ArgumentException("No namespace string found for typeName " + typeName);
		}

		// ==================================================================
		// Timing

		public static double NTiming (IFn f, int n)
		{
			var sw = new System.Diagnostics.Stopwatch();
			sw.Start();
			for (int i = 0; i < n; i++) {
				f.invoke();
			}
			sw.Stop();
			return sw.Elapsed.TotalMilliseconds / n;
		}

	}
}
#endif