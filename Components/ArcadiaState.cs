#if NET_4_6
using UnityEngine;
using System.Collections.Generic;
using clojure.lang;
using Arcadia;
using System;
using UnityEngine.Serialization;

[ExecuteInEditMode]
public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
	// TODO Figure out stylistic best practices for this sort of thing, bit of a mess right now

	// TODO sorted maps?
	[FormerlySerializedAs("edn")]
	public string serializedData = "{}";
	public string serializedFormat = "edn";
	public JumpMap state = new JumpMap();

	public string[] conversionKeys;
	public string[] conversionVars;
	public IPersistentMap conversions;

	// copy control
	public enum CopyStatus { Normal, Source, Receiver };
	public CopyStatus copyStatus = CopyStatus.Normal;

	public Atom objectDatabase = null;
	public int[] objectDatabaseIds = new int[0];
	public UnityEngine.Object[] objectDatabaseObjects = new UnityEngine.Object[0];

	[System.NonSerialized]
	public bool fullyInitialized = false;

	// creates objectDatabase atom from
	// objectDatabaseIds and objectDatabaseObjects
	public void BuildDatabaseAtom (bool force = false)
	{
		if (objectDatabase == null || force) {
			var idsToObjectsMap = PersistentHashMap.EMPTY;

			if (objectDatabaseIds.Length > 0 && objectDatabaseObjects.Length > 0) {
				// TODO transients?
				int len = System.Math.Min(objectDatabaseIds.Length, objectDatabaseObjects.Length);
				for (int i = 0; i < len; i++) {
					idsToObjectsMap = (PersistentHashMap)idsToObjectsMap.assoc(objectDatabaseIds[i], objectDatabaseObjects[i]);
				}
			}

			objectDatabase = new Atom(idsToObjectsMap);
		}
	}

	void WipeDatabase ()
	{
		objectDatabase = new Atom(PersistentHashMap.EMPTY);
	}

	// =====================================================
	// Static Data

	public static Var dataReaders;

	public static Var awakeFn;

	public static Var jumpMapToMapVar;

	public static Var deserializeVar;

	public static Var objectDbVar;

	public static Var serializeVar;

	public static Var printReadablyVar;

	public static Var prStrVar;

	public static Var readStringVar;

	public static Var dataReadersVar;

	public static Var defaultConversion;

	public static bool varsInitialized = false;

	// =====================================================

	private static void InitializeOwnVars ()
	{
		if (varsInitialized)
			return;

		Arcadia.Util.require("arcadia.data"); // side-effects clojure.core/*data-readers*
		Arcadia.Util.getVar(ref dataReaders, "clojure.core", "*data-readers*");

		string stateHelpNs = "arcadia.internal.state-help";
		Arcadia.Util.require(stateHelpNs);
		Arcadia.Util.getVar(ref awakeFn, stateHelpNs, "awake");
		Arcadia.Util.getVar(ref jumpMapToMapVar, stateHelpNs, "jumpmap-to-map");
		Arcadia.Util.getVar(ref deserializeVar, stateHelpNs, "deserialize");
		Arcadia.Util.getVar(ref defaultConversion, stateHelpNs, "default-conversion");

		var arcadiaLiteralsNs = "arcadia.data";
		Arcadia.Util.require(arcadiaLiteralsNs);
		Arcadia.Util.getVar(ref serializeVar, arcadiaLiteralsNs, "*serialize*");
		Arcadia.Util.getVar(ref objectDbVar, arcadiaLiteralsNs, "*object-db*");

		var coreNs = "clojure.core";
		Arcadia.Util.getVar(ref printReadablyVar, coreNs, "*print-readably*");
		Arcadia.Util.getVar(ref prStrVar, coreNs, "pr-str");

		Arcadia.Util.getVar(ref readStringVar, "clojure.edn", "read-string");
		Arcadia.Util.getVar(ref dataReadersVar, "clojure.core", "data-readers");

		varsInitialized = true;
	}



	public void DeserializeState ()
	{
		deserializeVar.invoke(this);
	}

	public void DeserializeConversions ()
	{
		if (conversionKeys != null && conversionKeys.Length > 0) {
			conversions = Arcadia.Util.DeserializeKeyVarMap(conversionKeys, conversionVars);
		}
	}


	// Require vars and full deserialize.
	// Will eventually call GetComponent via RefreshAll, so can't be called during OnAfterDeserialize
	// Triggered by Awake, also by FullInit in ArcadiaBehaviour
	public void Initialize ()
	{
		if (fullyInitialized)
			return;

		// TODO: cache component access

		InitializeOwnVars();
		DeserializeState();
		DeserializeConversions();
		fullyInitialized = true;
	}

	public void Awake ()
	{
		Initialize();
	}

	public void OnBeforeSerialize ()
	{
		Initialize();

		WipeDatabase();
		Var.pushThreadBindings(RT.map(objectDbVar, objectDatabase, serializeVar, true, printReadablyVar, false));
		try {
			serializedData = (string)prStrVar.invoke(state.ToPersistentMap());
			// TODO optimize this
			var map = (PersistentHashMap)objectDatabase.deref();
			objectDatabaseIds = (int[])RT.seqToTypedArray(typeof(int), RT.keys(map));
			objectDatabaseObjects = (UnityEngine.Object[])RT.seqToTypedArray(typeof(UnityEngine.Object), RT.vals(map));
		} finally {
			Var.popThreadBindings();
		}

		// serialize conversions
		if (conversions != null) {
			var ss = Arcadia.Util.SerializeKeyVarMap((IKVReduce)conversions, conversionKeys, conversionVars);
			conversionKeys = ss.Item1;
			conversionVars = ss.Item2;
		} else {
			conversionKeys = (conversionKeys != null && conversionKeys.Length == 0) ? conversionKeys : new string[0];
			conversionVars = (conversionVars != null && conversionVars.Length == 0) ? conversionVars : new string[0];
		}
	}

	// need for ISerializationCallbackReceiver interface
	public void OnAfterDeserialize ()
	{

	}

	void OnDestroy ()
	{
		if (ReferenceEquals(HookStateSystem.arcadiaState, this)) {
			HookStateSystem.Clear();
		}
	}

	// ============================================================
	// retrieval

	public clojure.lang.IPersistentMap ToPersistentMap ()
	{
		return state.ToPersistentMap();
	}

	// ============================================================
	// modification
	// I suppose
	public void Refresh (object k)
	{

	}

	//public void RefreshAll ()
	//{
	//	var arcadiaBehaviours = gameObject.GetComponents<ArcadiaBehaviour>();
	//	for (var i = 0; i < arcadiaBehaviours.Length; i++) {
	//		arcadiaBehaviours[i].RefreshPamvs();
	//	}
	//}

	public bool ContainsKey (object k)
	{
		return state.ContainsKey(k);
	}

	public void Add (object k, object v)
	{
		state.Add((Keyword)k, v);
	}

	// Strategies for removing keys in the presence of a lookup
	// system distributed as cached views. Reference type KeyVals 
	// means the caches immediately reflect new vals, but make 
	// removal of values trickier.
	// One approach is to flag the KeyVals as obsolete or evacuated,
	// then check this flag on every cached lookup, falling back to
	// a lookup against the backing dictionary which may be accompanied
	// by replacing the evacuated KeyVal. This has the advantage of laziness
	// and avoiding scans or maintaining an index of the location of KeyVals
	// so they can be efficiently removed on their actual removal from the
	// dictionary, but incurs an extra boolean check for every lookup,
	// which may be a bit expensive at this level where we're counting every op,
	// and you'd still have to do a bit of dancing around for an evacuated KeyVal.
	// We're close to having the infrastructure for that though, so it might be
	// a path to explore more in the future.
	// Another approach is to maintain an index of where all the keyvals are.
	// This would make finding and updating them faster, but incur all the overhead,
	// in terms of speed, memory, and code complexity, of maintaining the index itself.
	// We don't want to do that right now.
	// The approach we're going with for the moment is to just scan everything
	// and remove matching KeyVals as soon as their key is deleted, on the premise
	// that it's the simplest thing to do and key deletions are probably pretty rare.
	public void Remove (object k)
	{
		state.Remove((Keyword)k);
		// TODO: consider caching this
		foreach (var x in GetComponents<ArcadiaBehaviour>()) {
			x.RemoveCachedKey(k);
		}
	}

	public void Clear ()
	{
		state.Clear();
	}

	// TODO add arity with default value
	public object ValueAtKey (object k)
	{
		object v;
		if (state.TryGetValue((Keyword)k, out v)) {
			return v;
		}
		return null;
	}

	// ============================================================
	// copying

	// TODO presumably can make this a lot faster though more
	// structure sharing, for now main objective is to outperform
	// edn serialization and deserialization


	public void CopyTo (ArcadiaState target)
	{
		state.CopyTo(target.state, defaultConversion, conversions, gameObject, target.gameObject);
		target.conversions = conversions;
	}
	
}
#endif