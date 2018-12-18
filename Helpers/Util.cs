#if NET_4_6
using System;
using UnityEngine;
using clojure.lang;
using System.Collections.Generic;
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

		public static void EnsureRequireVar ()
		{
			if (requireVar == null) {
				Invoke(RT.var("clojure.core", "require"),
					   Symbol.intern("arcadia.internal.namespace"));
				requireVar = RT.var("arcadia.internal.namespace", "quickquire");
			}
		}

		public static void require (string s)
		{
			EnsureRequireVar();
			Invoke(requireVar, Symbol.intern(s));
		}

		public static void require (Symbol s)
		{
			EnsureRequireVar();
			Invoke(requireVar, s);
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

		// TODO: get rid of this when var invocation debugged
		public static IFn AsIFn (IFn f)
		{
			Var v = f as Var;
			if (v != null) {
				return (IFn)v.getRawRoot();
			}
			return f;
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
		// qualified names of keywords, symbols, vars

		public static string QualifiedName (Symbol s)
		{
			if (s.Namespace != null)
				return s.Namespace + "/" + s.Name;
			return s.Name;
		}

		public static String QualifiedName (Keyword kw)
		{
			if (kw.Namespace != null) {
				return kw.Namespace + "/" + kw.Name;
			}
			return kw.Name;
		}

		public static String QualifiedName (Var v)
		{
			return QualifiedName(v.sym);
		}

		public static Tuple<string, string> SplitQualifiedName (string qualifiedName)
		{
			int i = qualifiedName.IndexOf('/');
			if (i == -1) {
				return new Tuple<string, string>(null, qualifiedName);
			}
			return new Tuple<string, string>(qualifiedName.Substring(0, i), qualifiedName.Substring(i));
		}

		// loads var namespace as it goes
		public static Var DeserializeVar (string name)
		{
			var ss = SplitQualifiedName(name);
			if (ss.Item1 != null) {
				require(ss.Item1);
				return RT.var(ss.Item1, ss.Item2);
			}
			throw new ArgumentException("Can only deserialize qualified Var names. Var name: " + name);
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

		public static IPersistentMap DictionaryToMap<T1, T2> (Dictionary<T1, T2> dict)
		{
			ITransientMap bldg = (ITransientMap)PersistentHashMap.EMPTY.asTransient();
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

		// ------------------------------------------------------------------
		// serialization thereof
		// as usual, change this when we have 0-alloc persistent map iteration

		class SerializeKeyVarMapRFn : AFn
		{
			string[] keysAr;
			string[] valsAr;
			int i = 0;

			public SerializeKeyVarMapRFn (string[] keysAr, string[] valsAr)
			{
				this.keysAr = keysAr;
				this.valsAr = valsAr;
			}

			public override object invoke (object arg, object kIn, object vIn)
			{
				Keyword k = kIn as Keyword;
				if (k != null) {
					keysAr[i] = QualifiedName(k);
				} else {
					throw new InvalidOperationException("Keys must be Keywords, instead got instance of " + kIn.GetType());
				}

				Var v = vIn as Var;
				if (v != null) {
					valsAr[i] = QualifiedName(v);
				} else {
					throw new InvalidOperationException("Vals must be Vars, instead got instance of " + vIn.GetType());
				}
				i++;
				return null;
			}
		}

		public static Tuple<string[], string[]> SerializeKeyVarMap (IKVReduce m, string[] keysArIn, string[] valsArIn)
		{
			int len = ((clojure.lang.Counted)m).count();
			string[] keysAr = (keysArIn != null && keysArIn.Length == len) ? keysArIn : new string[len];
			string[] valsAr = (valsArIn != null && valsArIn.Length == len) ? valsArIn : new string[len];

			m.kvreduce(new SerializeKeyVarMapRFn(keysAr, valsAr), null);

			return new Tuple<string[], string[]>(keysAr, valsAr);
		}

		// ------------------------------------------------------------------
		// object array filtering

		public static UnityEngine.Component[] WithoutNullObjects (Component[] objects)
		{
			int nulls = 0;
			foreach (Component obj in objects) {
				if (obj == null) {
					nulls++;
				}
			}

			if (nulls == 0)
				return objects;

			Component[] arr = new Component[objects.Length - nulls];
			int end = 0;
			for (int i = 0; i < objects.Length; i++) {
				if (objects[i] != null) {
					arr[end] = objects[i];
					end++;
				}
			}

			return arr;
		}

		public static UnityEngine.Object[] WithoutNullObjects (UnityEngine.Object[] objects)
		{
			foreach (var o in objects)
				if (o == null)
					return RemoveNullObjects(objects);
			return objects;
		}

		private static UnityEngine.Object[] RemoveNullObjects (UnityEngine.Object[] objects)
		{
			var list = new List<UnityEngine.Object>();
			foreach (var o in objects)
				if (o != null)
					list.Add(o);

			return list.ToArray();
		}

		// ------------------------------------------------------------------
		// deserialization thereof

		public static IPersistentMap DeserializeKeyVarMap (string[] ks, string[] vs)
		{
			ITransientMap m = (ITransientMap)PersistentHashMap.EMPTY.asTransient();
			for (int i = 0; i < ks.Length; i++) {
				m.assoc(Keyword.intern(ks[i]), DeserializeVar(vs[i]));
			}
			return m.persistent();
		}

		// ==================================================================
		// nil

		public static bool IsNull (object obj)
		{
			if (obj == null) {
				return true;
			}

			UnityEngine.Object obj2 = obj as UnityEngine.Object;
			return obj2 == null;
		}

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

		public static GameObject ToGameObject (object x)
		{

			GameObject g = x as GameObject;
			if (g != null) {
				return g;
			}

			Component c = x as Component;
			if (c != null) {
				return c.gameObject;
			}

			if (x == null || g is GameObject || c is Component) {
				return null;
			}

			throw new ArgumentException(
				"Expects instance of UnityEngine.GameObject or UnityEngine.Component, instead received instance of " + x.GetType(),
				nameof(x));
		}

		// We want a more informative error than that normally thrown
		// by CLR miscasts. Not checking for liveness here because
		// Unity will do that for us in all cases where we use this 
		// method in arcadia.core.
		public static GameObject CastToGameObject (object x)
		{
			GameObject g = x as GameObject;
			if (g != null) {
				return g;
			}

			if (x == null) {
				throw new ArgumentNullException(
					nameof(x), 
					"Expects UnityEngine.GameObject instance, instead got null");
			}

			throw new ArgumentException(
				"Expects instance of UnityEngine.GameObject, instead received instance of " + x.GetType(), 
				nameof(x));
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