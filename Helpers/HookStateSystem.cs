using System;
using UnityEngine;
using clojure.lang;

namespace Arcadia
{
	public static class HookStateSystem
	{
		// Please don't rely on any of the public members in here;
		// this class should be considered strictly an (extremely unstable)
		// implementation detail, not part of the public API.

		public static Var stateVar;

		// we can microoptimize this stuff away later
		// hm, this is circular if we're not careful
		public static object SlowerLookup (object gobj, object key)
		{
			return stateVar.invoke(gobj, key);
		}

		public class ExpectedLookup
		{
			public object currentObject;

			public object currentKey;

			public object currentState;

			// clojure map style k-v pairs
			public object[] otherKeyStates;

			public ExpectedLookup (object currentKey_, object currentObject_,
								   object currentState_, object[] otherKeyStates_)
			{
				currentKey = currentKey_;
				currentObject = currentObject_;
				currentState = currentState_;
				otherKeyStates = otherKeyStates_;
			}

			// just for convenience/testing/etc
			public void assoc (object k, object v)
			{
				object[] newAr = new object[otherKeyStates.Length + 2];
				Array.Copy(otherKeyStates, newAr, otherKeyStates.Length);
				newAr[newAr.Length - 2] = k;
				newAr[newAr.Length - 1] = v;
				otherKeyStates = newAr;
			}

			public object LookupByKeyState (object gobj, object key)
			{
				for (int i = 0; i < otherKeyStates.Length; i += 2) {
					if (otherKeyStates[i] == key) {
						return otherKeyStates[i + 1];
					}
				}
				return SlowerLookup(gobj, key);
			}

			public object GetState (object gobj, object key)
			{
				if (gobj == currentObject) {
					if (key == currentKey) {
						return currentState;
					}
					return LookupByKeyState(gobj, key);
				}
				return SlowerLookup(gobj, key);
			}

		}

		public static ExpectedLookup currentLookup;

		public static object GetState (object gobj, object key)
		{
			return currentLookup.GetState(gobj, key);
		}
	}
}
