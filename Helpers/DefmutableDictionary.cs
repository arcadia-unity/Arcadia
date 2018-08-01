#if NET_4_6
using System;
using System.Collections.Generic;
using System.Linq;
using clojure.lang;
namespace Arcadia
{
	public class DefmutableDictionary
	{
		public Dictionary<clojure.lang.Keyword, System.Object> dict;

		public DefmutableDictionary ()
		{
			dict = new Dictionary<Keyword, object>();
		}

		public DefmutableDictionary (clojure.lang.IPersistentMap map)
		{
			dict = new Dictionary<Keyword, object>();
			foreach (var entry in map) {
				dict.Add((clojure.lang.Keyword)entry.key(), entry.val());
			}
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
	}
}
#endif