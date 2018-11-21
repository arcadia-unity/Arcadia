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
		// this is actually a GameObject but we don't want to compare it as such
		public static object cachedGameObject;
		// checking for null ArcadiaState etc is weird
		public static bool hasState = false;

		// be a bit careful here. be sure we don't try
		// to use a stale cache, for example when calling
		// `state` outside a Unity message
		public static int ifnInfoIndex;
		public static ArcadiaBehaviour.IFnInfo[] ifnInfos;

		public static void SetState (ArcadiaState arcadiaState_, GameObject go_, ArcadiaBehaviour.IFnInfo[] ifnInfos_)
		{
			arcadiaState = arcadiaState_;
			cachedGameObject = go_;
			ifnInfos = ifnInfos_;
			hasState = true;
		}

		public static void Clear ()
		{
			arcadiaState = null;
			cachedGameObject = null;
			ifnInfos = null;
			hasState = false;
		}

		// filling up to 4 and sticking there for now
		public static void UpdateCache (JumpMap.KeyVal kv)
		{
			if (ifnInfos[ifnInfoIndex].cacheSize < 4) {
				ArcadiaBehaviour.IFnInfo inf = ifnInfos[ifnInfoIndex];
				switch (inf.cacheSize) {
				case 0:
					inf.cacheSize = 1;
					inf.kv1 = kv;
					break;
				case 1:
					inf.cacheSize = 2;
					inf.kv2 = kv;
					break;
				case 2:
					inf.cacheSize = 3;
					inf.kv3 = kv;
					break;
				case 3:
					inf.cacheSize = 4;
					inf.kv4 = kv;
					break;
				}
				ifnInfos[ifnInfoIndex] = inf;
			}
		}

		public static object LookupTest (object key)
		{
			object val;
			if (ifnInfos[ifnInfoIndex].TryGetVal(key, out val)) {
				return val;
			}
			return null;
		}
				
		public static object Lookup (object gobj, object key)
		{
			// object/object comparison faster
			if (hasState && gobj == cachedGameObject){

				object val;
				if (ifnInfos[ifnInfoIndex].TryGetVal(key, out val)) {
					return val;
				}

				// update cache
				JumpMap.KeyVal kv;
				if (arcadiaState.state.TryGetKeyVal(key, out kv)) {
					UpdateCache(kv);
					return kv.val;
				}
				return null;
			}

			ArcadiaState state = ((GameObject)gobj).GetComponent<ArcadiaState>();
			if (state != null) {
				return state.ValueAtKey(key);
			}
			return null;
		}
	}
}
#endif