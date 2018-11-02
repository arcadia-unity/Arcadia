#if NET_4_6
using UnityEngine;
using System.Collections.Generic;
using clojure.lang;
using System.Linq;

// for starters, I think we should build a version that deals with a missing
// key someone wants access to by distributing an uninhabited kv pair that falls back on a runtime
// hashmap lookup. Then we can optimize that away once the rest of the logic is confirmed to work. 

namespace Arcadia
{
	public class JumpMap
	{
		// make private!
		public Dictionary<object, KeyVal> dict;


		// ==========================================================
		// constructor 

		public JumpMap ()
		{
			dict = new Dictionary<object, KeyVal>(53);
		}

		// ==========================================================
		// instance methods

		public KeyVal[] KeyVals ()
		{
			return dict.Values.ToArray();
		}

		public object[] Keys ()
		{
			return dict.Keys.ToArray();
		}

		public object[] Vals ()
		{
			return dict.Values.Select(val => val.val).ToArray();
		}

		public object ValueAtKey (object k)
		{
			KeyVal val = null;
			if (dict.TryGetValue(k, out val)) {
				// NOT val.GetVal()
				return val.val;
			} else {
				return null;
			}
		}

		public bool TryGetValue (object key, out object val)
		{
			KeyVal val2;
			if (dict.TryGetValue(key, out val2)) {
				val = val2.val;
				return true;
			}
			val = null;
			return false;
		}

		public bool TryGetKeyVal (object key, out KeyVal keyval)
		{
			return dict.TryGetValue(key, out keyval);
		}

		// sadly it seems we will need null keyvals
		// do we need them EVERY time we ask?
		// let us say we do not
		public KeyVal KeyValAtKey (object k)
		{
			KeyVal val = null;
			dict.TryGetValue(k, out val);
			return val;
		}

		// here's a place to optimize later
		public KeyVal Subscribe (object k)
		{
			KeyVal kv = KeyValAtKey(k);
			// not hanging onto this for now (would need GC stuff to prevent memory leak):
			if (kv == null) {
				kv = new KeyVal(k, null, this, false);
			}
			return kv;
		}

		public IPersistentMap ToPersistentMap ()
		{
			ATransientMap building = (ATransientMap)PersistentHashMap.EMPTY.asTransient();
			foreach (var e in dict) {
				building.assoc(e.Key, e.Value.val);
			}
			return building.persistent();
		}

		// ----------------------------------------------------------
		// beginnings of System.Collections.IDictionary

		public bool ContainsKey (object k)
		{
			return dict.ContainsKey(k) && KeyValAtKey(k).isInhabited;
		}

		public void Add (object k, object v)
		{
			if (ContainsKey(k)) {
				KeyValAtKey(k).val = v;
			} else {
				KeyVal kv = new KeyVal(k, v, this, true);
				dict.Add(k, kv);
			}
		}

		public void AddAll (clojure.lang.IPersistentMap map)
		{
			foreach (var entry in map) {
				Add(entry.key(), entry.val());
			}
		}

		public void Clear ()
		{
			var ks = dict.Keys.ToArray();
			for (int i = 0; i < ks.Length; i++) {
				Remove(ks[i]);
			}
		}

		public void Remove (object k)
		{
			KeyVal kv = KeyValAtKey(k);
			if (kv != null) {
				kv.Evacuate();
				dict.Remove(k);
			}
		}

		// ==========================================================
		// duplication

		public JumpMap CopyTo (JumpMap jm2, IFn defaultConversion, ILookup conversions, object sourceObject, object targetObject)
		{
			// TODO: clean this up when we get generic persistent lookup
			if (conversions != null) {
				foreach (var e in dict) {
					object key = e.Key;
					IFn conversion = defaultConversion;
					object tempConversion = conversions.valAt(key);
					if (tempConversion != null) {
						conversion = Arcadia.Util.AsIFn((IFn)tempConversion);
					}
					jm2.Add(key, conversion.invoke(key, e.Value.val, sourceObject, targetObject));
				}
			} else {
				foreach (var e in dict) {
					jm2.Add(e.Key, defaultConversion.invoke(e.Key, e.Value.val, sourceObject, targetObject));
				}
			}
			return jm2;
		}

		// ==========================================================
		// KeyVal

		public class KeyVal
		{
			public readonly object key;
			public object val;

			// here's where we get just terrible
			public bool isInhabited;

			public JumpMap jumpMap;

			public KeyVal (object _key, object _val, JumpMap _jumpMap, bool _isInhabited)
			{
				key = _key;
				val = _val;
				jumpMap = _jumpMap;
				isInhabited = _isInhabited;
			}

			public void Evacuate ()
			{
				// key is probably interned keyword anyway
				isInhabited = false;
				val = null;
			}

			public object GetVal ()
			{
				if (isInhabited)
					return val;
				return this.jumpMap.ValueAtKey(key);
			}

			// If the corresponding KeyVal is there now, returns it, 
			// else returns null.
			public KeyVal Refreshed ()
			{
				if (isInhabited)
					return this;
				KeyVal kv;
				if (jumpMap.TryGetKeyVal(key, out kv)) {
					return kv;
				}
				return null;
			}

		}

		// ==========================================================
		// View

		public class PartialArrayMapView
		{
			public KeyVal[] kvs;
			public JumpMap source;

			public object[] keys {
				get {
					object[] ks = new object[kvs.Length];
					for (int i = 0; i < kvs.Length; i++) {
						ks[i] = kvs[i].key;
					}
					return ks;
				}
			}

			public PartialArrayMapView (object[] keys, JumpMap source_)
			{
				if (source_ == null) {
					throw new System.Exception("source_ must be JumpMap, instead got null");
				}
				kvs = new KeyVal[keys.Length];
				source = source_;
				for (int i = 0; i < keys.Length; i++) {
					kvs[i] = source.Subscribe(keys[i]);
				}
			}

			public object ValueAtKey (object key)
			{
				// consider (and benchmark) foreach rather than for loop
				for (int i = 0; i < kvs.Length; i++) {
					if (kvs[i].key == key) {
						// if we want to get cute we can inline GetVal here
						return kvs[i].GetVal();
					}
				}
				return source.ValueAtKey(key);
			}

			public void Refresh ()
			{
				for (int i = 0; i < kvs.Length; i++) {
					if (!kvs[i].isInhabited && source.ContainsKey(kvs[i].key)) {
						kvs[i] = source.Subscribe(kvs[i].key);
					}
				}
			}

		}

		public PartialArrayMapView pamv (object[] keys)
		{
			return new PartialArrayMapView(keys, this);
		}

	}
}
#endif