using UnityEngine;
using System.Collections.Generic;
using clojure.lang;
using System.Linq;
using Arcadia;

[RequireComponent(typeof(ArcadiaState))]
public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	[SerializeField]
	public string edn = "{}";

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

		public IFnInfo (object key, IFn fn, JumpMap.PartialArrayMapView pamv)
		{
			this.key = key;
			this.fn = fn;
			this.pamv = pamv;
		}
	}

	// ============================================================
	// data

	private IFnInfo[] ifnInfos_;

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

	public ArcadiaState arcadiaState;

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

	private static Var hookStateSerializedEdnFn;

	private static Var addFnFn;

	private static Var removeFnFn;

	private static Var removeAllFnsFn;

	private static Var buildHookStateFn;

	public static Var requireVarNamespacesFn;

	private static void initializeVars ()
	{
		string nsStr = "arcadia.internal.hook-help";
		Arcadia.Util.require(nsStr);
		Arcadia.Util.getVar(ref hookStateDeserializeFn, nsStr, "hook-state-deserialize");
		Arcadia.Util.getVar(ref hookStateSerializedEdnFn, nsStr, "hook-state-serialized-edn");
		Arcadia.Util.getVar(ref addFnFn, nsStr, "add-fn");
		Arcadia.Util.getVar(ref removeFnFn, nsStr, "remove-fn");
		Arcadia.Util.getVar(ref removeAllFnsFn, nsStr, "remove-all-fns");
		Arcadia.Util.getVar(ref buildHookStateFn, nsStr, "build-hook-state");
		Arcadia.Util.getVar(ref requireVarNamespacesFn, nsStr, "require-var-namespaces");
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
		if (!_fullyInitialized)
			Init();

		for (int i = 0; i < ifnInfos_.Length; i++) {
			var inf = ifnInfos_[i];
			if (inf.key == key) {
				InvalidateIndexes();
				// shift over
				if (i != ifnInfos_.Length - 1)
					System.Array.Copy(ifnInfos_, i + 1, ifnInfos_, i, ifnInfos_.Length - (i + 1));
				ifnInfos_[ifnInfos_.Length - 1] = new IFnInfo(key, f, arcadiaState.pamv(fastKeys));
				return;
			}
		}

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
		if (!_fullyInitialized)
			Init();

		ifnInfos = new IFnInfo[0];
	}

	public void RemoveFunction (object key)
	{
		if (!_fullyInitialized)
			Init();

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

	public void Init ()
	{
		if (_fullyInitialized)
			return;

		initializeVars();
		hookStateDeserializeFn.invoke(this);
	}

	public void FullInit ()
	{
		if (_fullyInitialized)
			return;

		Init();
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
		edn = (string)hookStateSerializedEdnFn.invoke(this);
	}

	public void OnAfterDeserialize ()
	{
#if UNITY_EDITOR
		Init();
#endif
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