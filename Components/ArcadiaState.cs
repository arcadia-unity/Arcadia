#if NET_4_6
using UnityEngine;
using System.Collections;
using clojure.lang;
using Arcadia;

[ExecuteInEditMode]
public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
	// TODO sorted maps?
	public string edn = "{}";
	public JumpMap state = new JumpMap();

	public Atom objectDatabase = null;
	public int[] objectDatabaseIds = new int[0];
	public Object[] objectDatabaseObjects = new Object[0];

	private static IFn prStr = null;
	private static IFn readString = null;
	private static IFn requireFn = null;

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

	public static bool varsInitialized = false;

	// =====================================================

	private static void InitializeOwnVars ()
	{
		if (varsInitialized)
			return;

		string literalsNs = "arcadia.literals";
		Arcadia.Util.require(literalsNs);
		Arcadia.Util.getVar(ref dataReaders, literalsNs, "*data-readers*");

		string stateHelpNs = "arcadia.internal.state-help";
		Arcadia.Util.require(stateHelpNs);
		Arcadia.Util.getVar(ref awakeFn, stateHelpNs, "awake");
		Arcadia.Util.getVar(ref jumpMapToMapVar, stateHelpNs, "jumpmap-to-map");
		Arcadia.Util.getVar(ref deserializeVar, stateHelpNs, "deserialize");

		varsInitialized = true;
	}


	// Require vars and full deserialize.
	// Will eventually call GetComponent via RefreshAll, so can't be called during OnAfterDeserialize
	// Triggered by Awake, also by FullInit in ArcadiaBehaviour
	public void Initialize ()
	{
		if (fullyInitialized)
			return;

		InitializeOwnVars();
		deserializeVar.invoke(this);
		RefreshAll();
		fullyInitialized = true;
	}

	public void Awake ()
	{
		Initialize();
	}

	public void OnBeforeSerialize ()
	{
		// experimental:
		Initialize();

		if (prStr == null) prStr = (IFn)RT.var("clojure.core", "pr-str");
		Arcadia.Util.require("arcadia.literals");
		Namespace ArcadiaLiteralsNamespace = Namespace.findOrCreate(Symbol.intern("arcadia.literals"));
		Var ObjectDbVar = Var.intern(ArcadiaLiteralsNamespace, Symbol.intern("*object-db*")).setDynamic();

		InitializeOwnVars();

		WipeDatabase();
		Var.pushThreadBindings(RT.map(ObjectDbVar, objectDatabase));
		try {
			edn = (string)prStr.invoke(jumpMapToMapVar.invoke(state)); // side effects, updating objectDatabase
			var map = (PersistentHashMap)objectDatabase.deref();
			objectDatabaseIds = (int[])RT.seqToTypedArray(typeof(int), RT.keys(map));
			objectDatabaseObjects = (Object[])RT.seqToTypedArray(typeof(Object), RT.vals(map));
		} finally {
			Var.popThreadBindings();
		}
	}

	public void OnAfterDeserialize ()
	{

	}

	void OnDestroy ()
	{
		if (ReferenceEquals(HookStateSystem.arcadiaState, this)) {
			HookStateSystem.hasState = false;
		}
	}

	// ============================================================
	// retrieval

	public object[] Keys ()
	{
		return state.Keys();
	}

	public object[] Vals ()
	{
		return state.Vals();
	}

	public clojure.lang.IPersistentMap ToPersistentMap ()
	{
		return Arcadia.Util.Zipmap(Keys(), Vals());
	}

	// ============================================================
	// modification
	public void RefreshAll ()
	{
		var arcadiaBehaviours = gameObject.GetComponents<ArcadiaBehaviour>();
		for (var i = 0; i < arcadiaBehaviours.Length; i++) {
			arcadiaBehaviours[i].RefreshPamvs();
		}
	}

	public void Add (object k, object v)
	{
		bool hadKey = state.ContainsKey(k);
		state.Add(k, v);
		// determine if this warrants refreshing the pamv's
		if (!hadKey) {
			RefreshAll();
		}
	}

	public void Remove (object k)
	{
		state.Remove(k);
		// don't need to refresh anything
	}

	public void Clear ()
	{
		state.Clear();
	}

	public object ValueAtKey (object k)
	{
		return state.ValueAtKey(k);
	}

	public JumpMap.PartialArrayMapView pamv (object[] ks)
	{
		Debug.Log("In ArcadiaState.pamv. ks.Length:" + ks.Length);
		return state.pamv(ks);
	}
}
#endif