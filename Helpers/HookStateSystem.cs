#if NET_4_6
using System;
using UnityEngine;
using clojure.lang;
using System.Collections.Generic;
using Arcadia;

namespace Arcadia
{
	public class HookStateSystem
	{
		// at least start out *real* simple
		public static ArcadiaState arcadiaState;

		// checking for null ArcadiaState etc is weird
		public static bool hasState = false;

		public static JumpMap.PartialArrayMapView pamv;

		public static object FullLookup (object obj, object key)
		{
			ArcadiaState arcs;

			var gobj = obj as GameObject;
			if (gobj != null) {
				arcs = gobj.GetComponent<ArcadiaState>();
			} else {
				var cmpt = obj as Component;
				if (cmpt != null) {
					arcs = cmpt.GetComponent<ArcadiaState>();
				} else {
					return null;
				}
			}

			if (arcs == null)
				return null;

			var jm = arcs.state;
			Arcadia.JumpMap.KeyVal kv;
			if (jm.dict.TryGetValue(key, out kv)) {
				return kv.val;
			}
			return null;
		}

		public static object Lookup (object gobj, object key)
		{
			if (hasState && gobj == arcadiaState.gameObject) {
				var kvs = pamv.kvs;
				for (int i = 0; i < kvs.Length; i++) {
					var kv = kvs[i];
					if (kv.key == key) {
						if (kv.isInhabited)
							return kv.val;
						return kv.jumpMap.ValueAtKey(key);
					}
				}
			}
			return FullLookup(gobj, key);
		}

	}
}
#endif