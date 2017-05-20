using UnityEngine;
//using System.Collections.Generic;
using clojure.lang;

public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	[SerializeField]
	public string edn = "{}";

	[System.NonSerialized]
	protected bool _fullyInitialized = false;

	public bool fullyInitialized 
	{
		get {
			return _fullyInitialized;
		}
	}
		
	// so we can avoid the whole question of defrecord, contravariance, etc for now
	public class StateContainer
	{
		public readonly IPersistentMap indexes;
		public readonly IFn[] fns;
		public readonly object[] keys;

		public StateContainer ()
		{
			indexes = PersistentHashMap.EMPTY;
			fns = new IFn[0];
			keys = new Object[0];
		}

		public StateContainer (IPersistentMap _indexes, object[] _keys, object[] _fns)
		{
			indexes = _indexes;
			fns = new IFn[_fns.Length];
			keys = _keys;
			for (var i = 0; i < fns.Length; i++) {
				fns[i] = (IFn)_fns[i];
			}
		}
	}

	public Atom state = new Atom(new StateContainer());

	private static IFn requireFn = null;

	private static IFn hookStateDeserializeFn = null;

	private static IFn hookStateSerializedEdnFn = null;

	private static IFn addFnFn = null;

	private static IFn removeFnFn = null;

	private static IFn removeAllFnsFn = null;

	private static IFn buildHookStateFn = null;

	public static IFn requireVarNamespacesFn = null;

	public IPersistentMap indexes 
	{
		get {
			return ((StateContainer)state.deref()).indexes;
		}
	}

	public IFn[] fns
	{		
		get {
			return ((StateContainer)state.deref()).fns;
		}
	}

	public object[] keys 
	{
		get {
			return ((StateContainer)state.deref()).keys;
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
		string nsStr = "arcadia.internal.hook-help";
		require(nsStr);
		if (hookStateDeserializeFn == null)
			hookStateDeserializeFn = RT.var(nsStr, "hook-state-deserialize");
		if (hookStateSerializedEdnFn == null)
			hookStateSerializedEdnFn = RT.var(nsStr, "hook-state-serialized-edn");
		if (addFnFn == null)
			addFnFn = RT.var(nsStr, "add-fn");
		if (removeFnFn == null)
			removeFnFn = RT.var(nsStr, "remove-fn");
		if (removeAllFnsFn == null)
			removeAllFnsFn = RT.var(nsStr, "remove-all-fns");
		if (buildHookStateFn == null)
			buildHookStateFn = RT.var(nsStr, "build-hook-state");
		if (requireVarNamespacesFn == null)
			requireVarNamespacesFn = RT.var(nsStr, "require-var-namespaces");
	}

	public void OnBeforeSerialize()
	{
		edn = (string)hookStateSerializedEdnFn.invoke(this);
	}

	public void AddFunction (IFn f)
	{
		if (!fullyInitialized) {
			Init();
		}
		AddFunction(f, f);
	}

	public void AddFunction (IFn f, object key)
	{
		if (!fullyInitialized) {
			Init();
		}
		addFnFn.invoke(this, key, f);
	}

	public void RemoveAllFunctions ()
	{
		if (!fullyInitialized) {
			Init();
		}
		removeAllFnsFn.invoke(this);
	}

	public void RemoveFunction (object key)
	{
		if (!fullyInitialized) {
			Init();
		}
		removeFnFn.invoke(this, key);
	}

	public void OnAfterDeserialize()
	{
#if UNITY_EDITOR
		Init();
#endif
	}

	public virtual void Awake()
	{
		FullInit();
	}

	public void Init() {
		initializeVars();
		hookStateDeserializeFn.invoke(this);		
	}

	public void FullInit() {
		Init();
		requireVarNamespacesFn.invoke(this);
		_fullyInitialized = true;
	}

	// ============================================================
	// RunFunctions

	public void RunFunctions ()
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		var _fns = fns;
		var _keys = keys;
		for (int i = 0; i < _fns.Length; i++) {
			_fns[i].invoke(_go, _keys[i]);
		}		
	}

	public void RunFunctions (object arg1)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		var _fns = fns;
		var _keys = keys;
		for (int i = 0; i < _fns.Length; i++) {
			_fns[i].invoke(_go, _keys[i], arg1);
		}
	}

	public void RunFunctions (object arg1, object arg2)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		var _fns = fns;
		var _keys = keys;
		for (int i = 0; i < _fns.Length; i++) {
			_fns[i].invoke(_go, _keys[i], arg1, arg2);
		}
	}

	public void RunFunctions (object arg1, object arg2, object arg3)
	{
		if (!_fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		var _fns = fns;
		var _keys = keys;
		for (int i = 0; i < _fns.Length; i++) {
			_fns[i].invoke(_go, _keys[i], arg1, arg2, arg3);
		}
	}
}