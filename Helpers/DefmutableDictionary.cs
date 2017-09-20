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
			return Arcadia.Util.Zipmap(dict.Keys.ToArray(), dict.Values.ToArray());
		}
	}
}
