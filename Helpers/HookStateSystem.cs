using System;
using UnityEngine;
using clojure.lang;
using System.Collections.Generic;

namespace Arcadia
{
	public class HookStateSystem
	{
		// at least start out *real* simple
		public static ArcadiaState arcadiaState;

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
				return ((IPersistentMap)arcs.state.deref()).valAt(key);
			}
			return null;
		}

		public static object Lookup (object gobj, object key)
		{
			if (arcadiaState != null && gobj == arcadiaState.gameObject) {
				return ((IPersistentMap)arcadiaState.state.deref()).valAt(key);
			}
			return FullLookup(gobj, key);
		}
	}
}
