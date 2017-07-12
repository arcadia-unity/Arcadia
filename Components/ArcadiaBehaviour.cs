using UnityEngine;
using System.Collections.Generic;
using clojure.lang;
using System.Linq;
using Arcadia;

[RequireComponent(typeof(ArcadiaState))]
public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	[SerializeField]
	public string edn = "[]";

	[System.NonSerialized]
	protected bool _fullyInitialized = false;

	public bool fullyInitialized {
		get {
			return _fullyInitialized;
		}
	}

	public class IFnInfo
	{
		public object key;
		public IFn fn;
		public JumpMap.PartialArrayMapView pamv;

		// for the awkward pause between deserialization and connection to the scene graph
		public object[] fastKeys;

		public IFnInfo (object key, IFn fn, JumpMap.PartialArrayMapView pamv)
		{
			this.key = key;
			this.fn = fn;
			this.pamv = pamv;
		}

		public static IFnInfo LarvalIFnInfo (object key, IFn fn, object[] fastKeys)
		{
			var inf = new IFnInfo(key, fn, null);
			inf.fastKeys = fastKeys;
			return inf;
		}

		public bool IsLarval ()
		{
			return pamv == null;
		}

		public void Realize (JumpMap jm)
		{
			if (pamv == null) {
				if (fastKeys != null) {
					pamv = jm.pamv(fastKeys);
				} else {
					throw new System.Exception("Missing both pamv and fastKeys");
				}
			}
		}
	}

	// ============================================================
	// data

	private IFnInfo[] ifnInfos_ = new IFnInfo[0];

	// maybe this should be NonSerialized
	// [System.NonSerialized]
	public ArcadiaState arcadiaState;

	// compute indexes lazily
	private IPersistentMap indexes_;

	public IFnInfo[] ifnInfos {
		get {
			return ifnInfos_;
		}
		set {
			ifnInfos_ = value;
			InvalidateIndexes();
		}
	}

	public object[] keys {
		get {
			var arr = new object[ifnInfos_.Length];
			for (int i = 0; i < ifnInfos_.Length; i++) {
				arr[i] = ifnInfos_[i].key;
			}
			return arr;
		}
	}

	public IFn[] fns {
		get {
			var arr = new IFn[ifnInfos_.Length];
			for (int i = 0; i < ifnInfos_.Length; i++) {
				arr[i] = ifnInfos_[i].fn;
			}
			return arr;
		}
	}

	public IPersistentMap indexes {
		get {
			if (indexes_ == null)
				indexes_ = Arcadia.Util.Zipmap(keys, fns);
			return indexes_;
		}
	}

	// ============================================================
	// vars etc

	private static Var requireFn;

	private static Var hookStateDeserializeFn;

	private static Var serializeBehaviourFn;

	public static Var requireVarNamespacesFn;

	[System.NonSerialized]
	public static bool varsInitialized = false;

	private static void initializeVars ()
	{
		string nsStr = "arcadia.internal.hook-help";
		Arcadia.Util.require(nsStr);
		Arcadia.Util.getVar(ref hookStateDeserializeFn, nsStr, "hook-state-deserialize");
		Arcadia.Util.getVar(ref serializeBehaviourFn, nsStr, "serialize-behaviour");
		Arcadia.Util.getVar(ref requireVarNamespacesFn, nsStr, "require-var-namespaces");

		varsInitialized = true;
	}

	// ============================================================

	public void InvalidateIndexes ()
	{
		indexes_ = null;
	}

	public void AddFunction (IFn f, object key)
	{
		AddFunction(f, key, new object[0]);
	}

	public void AddFunction (IFn f, object key, object[] fastKeys)
	{
		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			if (inf.key == key) {
				InvalidateIndexes();
				// shift over
				if (i < ifnInfos_.Length - 1)
					Arcadia.Util.WindowShift(ifnInfos_, i + 1, ifnInfos_.Length - 1, i);
				ifnInfos_[ifnInfos_.Length - 1] = new IFnInfo(key, f, arcadiaState.pamv(fastKeys));
				return;
			}
		}

		if (arcadiaState == null)
			arcadiaState = GetComponent<ArcadiaState>();

		ifnInfos = Arcadia.Util.ArrayAppend(
			ifnInfos_,
			new IFnInfo(key, f, arcadiaState.pamv(fastKeys)));
	}

	public void AddFunctions (IFnInfo[] newIfnInfos)
	{
		var ixs = indexes;
		var newKeys = new HashSet<System.Object>(newIfnInfos.Select(inf => inf.key));

		ifnInfos = ifnInfos
			.Where(inf => !newKeys.Contains(inf.key))
			.Concat(newIfnInfos)
			.ToArray();
	}

	public void RemoveAllFunctions ()
	{
		ifnInfos = new IFnInfo[0];
	}

	public void RemoveFunction (object key)
	{
		int inx = -1;
		for (int i = 0; i < ifnInfos_.Length; i++) {
			if (ifnInfos_[i].key == key) {
				inx = i;
				break;
			}
		}
		if (inx != -1) {
			ifnInfos = Arcadia.Util.ArrayRemove(ifnInfos_, inx);
		}
	}


	// ============================================================
	// setup

	public void RealizeAll (JumpMap jm)
	{
		foreach (var inf in ifnInfos) {
			if (inf.IsLarval()) {
				inf.Realize(jm);
			}
		}
	}

	public void FullInit ()
	{
		if (_fullyInitialized)
			return;

		//Init();
		initializeVars();
		arcadiaState = GetComponent<ArcadiaState>();
		arcadiaState.Initialize();
		requireVarNamespacesFn.invoke(this);
		_fullyInitialized = true;
	}

	public void RefreshPamvs ()
	{
		for (int i = 0; i < ifnInfos_.Length; i++) {
			ifnInfos_[i].pamv.Refresh();
		}
	}

	// ============================================================
	// messages

	public virtual void Awake ()
	{
		FullInit();
	}

	public void OnBeforeSerialize ()
	{
		Debug.Log("In OnBeforeSerialize for ArcadiaBehaviour");
		if (arcadiaState == null)
			arcadiaState = GetComponent<ArcadiaState>();
		if (!varsInitialized)
			initializeVars();
		// will populate potentially larval ArcadiaBehaviours
		RealizeAll(GetComponent<ArcadiaState>().state);
		edn = (string)serializeBehaviourFn.invoke(this);
	}

	public void OnAfterDeserialize ()
	{
		Debug.Log("In OnAfterDeserialize for ArcadiaBehaviour");
		Debug.Log("edn:\n" + edn);
		if (!varsInitialized)
			initializeVars();
		hookStateDeserializeFn.invoke(this);
	}

	// ============================================================
	// RunFunctions

	public void RunFunctions ()
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		HookStateSystem.arcadiaState = arcadiaState;
		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			HookStateSystem.pamv = inf.pamv;
			var v = inf.fn as Var;
			if (v != null) {
				((IFn)v.getRawRoot()).invoke(_go, inf.key);
			} else {
				inf.fn.invoke(_go, inf.key);
			}
		}
	}

	public void RunFunctions (object arg1)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		HookStateSystem.arcadiaState = arcadiaState;
		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			HookStateSystem.pamv = inf.pamv;
			var v = inf.fn as Var;
			if (v != null) {
				((IFn)v.getRawRoot()).invoke(_go, arg1, inf.key);
			} else {
				inf.fn.invoke(_go, arg1, inf.key);
			}
		}
	}

	public void RunFunctions (object arg1, object arg2)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		HookStateSystem.arcadiaState = arcadiaState;
		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			HookStateSystem.pamv = inf.pamv;
			var v = inf.fn as Var;
			if (v != null) {
				((IFn)v.getRawRoot()).invoke(_go, arg1, arg2, inf.key);
			} else {
				inf.fn.invoke(_go, arg1, arg2, inf.key);
			}
		}
	}

	public void RunFunctions (object arg1, object arg2, object arg3)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		HookStateSystem.arcadiaState = arcadiaState;
		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			HookStateSystem.pamv = inf.pamv;
			var v = inf.fn as Var;
			if (v != null) {
				((IFn)v.getRawRoot()).invoke(_go, arg1, arg2, arg3, inf.key);
			} else {
				inf.fn.invoke(_go, arg1, arg2, arg3, inf.key);
			}
		}
	}
}