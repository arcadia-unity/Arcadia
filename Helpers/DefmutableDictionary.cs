#if NET_4_6
using System;
using System.Collections.Generic;
using System.Linq;
using clojure.lang;
namespace Arcadia
{
	public class DefmutableDictionary
	{
		public Dictionary<object, object> dict;

		public System.Collections.ICollection Keys {
			get {
				return dict.Keys;
			}
		}

		public System.Collections.ICollection Values {
			get {
				return dict.Values;
			}
		}

		public int Count { 
			get { return dict.Count; }
		}

		public bool Contains (object key) {
			return dict.ContainsKey(key);
		}

		public void Remove (object key)
		{
			dict.Remove(key);
		}

		public DefmutableDictionary ()
		{
			dict = new Dictionary<object, object>();
		}

		public DefmutableDictionary (IDictionary<object,object> map)
		{
			dict = new Dictionary<object, object>(map);
		}

		// not trying to implement clojure interfaces yet, this is all internal stuff
		public bool ContainsKey (clojure.lang.Keyword kw)
		{
			return dict.ContainsKey(kw);
		}

		public object GetValue (clojure.lang.Keyword kw)
		{
			object val;
			dict.TryGetValue(kw, out val);
			return val;
		}

		public void Add (clojure.lang.Keyword kw, object obj)
		{
			dict.Add(kw, obj);
		}

		public bool Remove (clojure.lang.Keyword kw)
		{
			return dict.Remove(kw);
		}

		public clojure.lang.IPersistentMap ToPersistentMap ()
		{
			var len = dict.Count * 2;
			var kvs = new object[len];
			int i = 0;
			foreach (var kv in dict) {
				kvs[i] = kv.Key;
				kvs[i + 1] = kv.Value;
				i+=2;
			}
			return PersistentHashMap.create(kvs);
		}

		public clojure.lang.IPersistentMap ToPersistentMap (object mutInst, IFn processElement)
		{
			var len = dict.Count * 2;
			var kvs = new object[len];
			int i = 0;
			foreach (var kv in dict) {
				kvs[i] = kv.Key;
				kvs[i + 1] = processElement.invoke(mutInst, kv.Key, kv.Value);
				i+=2;
			}
			return PersistentHashMap.create(kvs);
		}

		// for serialization of defmutable types as edn
		public void PrintEntries (IFn printfn, System.IO.TextWriter w)
		{
			bool first = true;
			foreach (var e in dict) {
				if (first) {
					first = false;
				} else {
					w.Write(", ");
				}
				printfn.invoke(e.Key, w);
				w.Write(" ");
				printfn.invoke(e.Value, w);
			}
		}
	}
}
#endif