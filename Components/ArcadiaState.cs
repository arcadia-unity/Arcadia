using UnityEngine;
using System.Collections;
using clojure.lang;
using Arcadia;

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
	// TODO: make all these private
	public static Var dataReaders;

	public static Var awakeFn;

	public static Var jumpMapToMapVar;

	public static Var initializeVar;

	public static bool varsInitialized = false;

	public static bool fullyInitialized_ = false;

	// =====================================================

	public bool fullyInitialized {
		get {
			return fullyInitialized_;
		}
	}

	private static void require (string s)
	{
		if (requireFn == null) {
			requireFn = RT.var("clojure.core", "require");
		}
		requireFn.invoke(Symbol.intern(s));
	}

	private static void initializeVars ()
	{
		if (varsInitialized)
			return;

		string nsStr = "arcadia.literals";
		require(nsStr);
		if (dataReaders == null)
			dataReaders = RT.var(nsStr, "*data-readers*");
		string nsStr2 = "arcadia.internal.state-help";
		require(nsStr2);
		if (awakeFn == null)
			awakeFn = RT.var(nsStr2, "awake");
		if (jumpMapToMapVar == null)
			jumpMapToMapVar = RT.var(nsStr2, "jumpmap-to-map");
		if (initializeVar == null)
			initializeVar = RT.var(nsStr2, "initialize");

		varsInitialized = true;
	}


	// require vars and full deserialize
	public void Initialize ()
	{
		if (fullyInitialized_)
			return;

		initializeVars();
		initializeVar.invoke(this);
		fullyInitialized_ = true;
	}

	public void Awake ()
	{
		Initialize();
	}

	public void OnBeforeSerialize ()
	{
		if (prStr == null) prStr = (IFn)RT.var("clojure.core", "pr-str");
		if (requireFn == null) requireFn = (IFn)RT.var("clojure.core", "require");
		requireFn.invoke(Symbol.intern("arcadia.literals"));
		Namespace ArcadiaLiteralsNamespace = Namespace.findOrCreate(Symbol.intern("arcadia.literals"));
		Var ObjectDbVar = Var.intern(ArcadiaLiteralsNamespace, Symbol.intern("*object-db*")).setDynamic();

		initializeVars();

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
		initializeVars();
		//deserializeVar.invoke();
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

	public object ValueAtKey (object k)
	{
		return state.ValueAtKey(k);
	}

	public JumpMap.PartialArrayMapView pamv (object[] ks)
	{
		return this.state.pamv(ks);
	}
}