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
				arcs = (ArcadiaState)gobj.GetComponent(typeof(ArcadiaState));
			} else {
				var cmpt = obj as Component;
				if (cmpt != null) {
					arcs = (ArcadiaState)cmpt.GetComponent(typeof(ArcadiaState));
				} else {
					throw new Exception(
						"obj must be GameObject or Component, instead got " + obj.GetType()
					);
				}
			}
			if (arcs != null) {
				return arcs.state.ValueAtKey(key);
			}
			return null;
		}

		public static object Lookup (object gobj, object key)
		{

			//if (arcadiaState != null && gobj == arcadiaState.gameObject) {
			//	if (pamv != null) {
			//		return pamv.ValueAtKey(key);
			//	}
			//	return arcadiaState.state.ValueAtKey(key);
			//}
			//return FullLookup(gobj, key)

			// fully inlined lookup:

			if (hasState && ReferenceEquals(gobj, arcadiaState.gameObject)) {
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
