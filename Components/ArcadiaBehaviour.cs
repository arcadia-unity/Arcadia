using UnityEngine;
//using System.Collections.Generic;
using clojure.lang;

public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	[SerializeField]
	public string edn = "{}";

	// so we can avoid the whole question of defrecord, contravariance, etc for now
	public class StateContainer
	{
		public readonly IPersistentMap indexes;
		public readonly IFn[] fns;

		public StateContainer ()
		{
			indexes = PersistentHashMap.EMPTY;
			fns = new IFn[0];
		}

		public StateContainer (IPersistentMap _indexes, System.Object[] _fns)
		{
			indexes = _indexes;
			fns = new IFn[_fns.Length];
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
	}

	public void OnBeforeSerialize()
	{
		edn = (string)hookStateSerializedEdnFn.invoke(this);
	}

	public void AddFunction (IFn f)
	{
		AddFunction(f, f);
	}

	public void AddFunction (IFn f, object key)
	{
		addFnFn.invoke(this, key, f);
	}

	public void RemoveAllFunctions ()
	{
		removeAllFnsFn.invoke(this);
	}

	public void RemoveFunction (object key)
	{
		removeFnFn.invoke(this, key);
	}

	public void OnAfterDeserialize()
	{
#if UNITY_EDITOR
		Init();
#endif
	}

	// if serializedVar not null, set fn to var
	public virtual void Awake()
	{
		Init();
	}

	private void Init() {
		initializeVars();
		hookStateDeserializeFn.invoke(this);
	}
}