#if NET_4_6
using UnityEngine;
using System;
using System.Collections.Generic;
using clojure.lang;
using System.Linq;
using Arcadia;

[RequireComponent(typeof(ArcadiaState))]
public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	[SerializeField]
	public string[] keyNames;

	[SerializeField]
	public string[] varNames;

	[System.NonSerialized]
	protected bool _fullyInitialized = false;

	[System.NonSerialized]
	protected GameObject _go;

	public bool fullyInitialized {
		get {
			return _fullyInitialized;
		}
	}

	public struct IFnInfo
	{
		public Keyword key;
		public IFn fn;
		// cached lookup fields
		public int cacheSize;
		public JumpMap.KeyVal kv1;
		public JumpMap.KeyVal kv2;
		public JumpMap.KeyVal kv3;
		public JumpMap.KeyVal kv4;

		public IFnInfo (Keyword key_, IFn fn_, int cacheSize_, JumpMap.KeyVal kv1_, JumpMap.KeyVal kv2_, JumpMap.KeyVal kv3_, JumpMap.KeyVal kv4_)
		{
			key = key_;
			fn = fn_;
			cacheSize = cacheSize_;
			kv1 = kv1_;
			kv2 = kv2_;
			kv3 = kv3_;
			kv4 = kv4_;
		}

		public IFnInfo (Keyword key_, IFn fn_)
		{
			key = key_;
			fn = fn_;
			cacheSize = 0;
			kv1 = null;
			kv2 = null;
			kv3 = null;
			kv4 = null;
		}

		public bool Lookup (object k, out object val)
		{
			switch (cacheSize) {
			case 0:
				break;
			case 1:
				if (kv1.key == k) {
					val = kv1.val;
					return true;
				}
				break;
			case 2:
				if (kv1.key == k) {
					val = kv1.val;
					return true;
				}
				if (kv2.key == k) {
					val = kv2.val;
					return true;
				}
				break;
			case 3:
				if (kv1.key == k) {
					val = kv1.val;
					return true;
				}
				if (kv2.key == k) {
					val = kv2.val;
					return true;
				}
				if (kv3.key == k) {
					val = kv3.val;
					return true;
				}
				break;
			case 4:
				if (kv1.key == k) {
					val = kv1.val;
					return true;
				}
				if (kv2.key == k) {
					val = kv2.val;
					return true;
				}
				if (kv3.key == k) {
					val = kv3.val;
					return true;
				}
				if (kv4.key == k) {
					val = kv4.val;
					return true;
				}
				break;
			default:
				throw new System.InvalidOperationException("size of SampleCacheStruct out of bounds");
			}
			val = null;
			return false;
		}

	}

	// ============================================================
	// for ISerializationCallbackReceiver

	// TODO	: Will not allow things like anonymous functions even in the inspector,
	// change this when basic principle validated

	void ISerializationCallbackReceiver.OnBeforeSerialize ()
	{
		keyNames = new string[ifnInfos.Length];
		varNames = new string[ifnInfos.Length];

		for (int i = 0; i < ifnInfos.Length; i++) {
			var inf = ifnInfos[i];
			keyNames[i] = inf.key.Name;
			Var v = inf.fn as Var;
			if (v != null) {
				varNames[i] = v.ToString();
			} else {
				throw new InvalidOperationException("Attempting to serialize non-Var function in ArcadiaBehaviour. Key: " + inf.key);
			}
		}
	}

	void ISerializationCallbackReceiver.OnAfterDeserialize ()
	{

	}

	// ============================================================
	// data

	// not sure we need the property thing. but it should get inlined anyway
	private IFnInfo[] ifnInfos_ = new IFnInfo[0]; // might not need to initialize this to empty?

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

	// private static Var requireFn;

	// private static Var hookStateDeserializeFn;

	// private static Var serializeBehaviourFn;

	// public static Var requireVarNamespacesFn;

	[System.NonSerialized]
	public static bool varsInitialized = false;

	private static void InitializeOwnVars ()
	{
		if (varsInitialized)
			return;

		string nsStr = "arcadia.internal.hook-help";
		Arcadia.Util.require(nsStr);
		//Arcadia.Util.getVar(ref hookStateDeserializeFn, nsStr, "hook-state-deserialize");
		//Arcadia.Util.getVar(ref serializeBehaviourFn, nsStr, "serialize-behaviour");
		//Arcadia.Util.getVar(ref requireVarNamespacesFn, nsStr, "require-var-namespaces");

		varsInitialized = true;
	}

	// ============================================================

	public void InvalidateIndexes ()
	{
		indexes_ = null;
	}

	// TODO: this is just to accomodate legacy fastkeys stuff until 
	// we get rid of it
	public void AddFunction (IFn f, Keyword key, object stuff)
	{
		AddFunction(f, key);
	}

	public void AddFunction (IFn f, Keyword key)
	{
		FullInit();
		// just treat it like an associative array for now
		for (int i = 0; i < ifnInfos.Length; i++) {
			if (ifnInfos[i].key == key) {
				ifnInfos[i] = new IFnInfo(key, f);
				InvalidateIndexes();
				return;
			}
		}
		ifnInfos = Arcadia.Util.ArrayAppend(ifnInfos, new IFnInfo(key, f));
		InvalidateIndexes();

		//for (int i = 0; i < ifnInfos_.Length; i++) {
		//	var inf = ifnInfos_[i];
		//	if (inf.key == key) {
		//		InvalidateIndexes();
		//		// shift over
		//		if (i < ifnInfos_.Length - 1)
		//			Arcadia.Util.WindowShift(ifnInfos_, i + 1, ifnInfos_.Length - 1, i);
		//		ifnInfos_[ifnInfos_.Length - 1] = new IFnInfo(key, f);
		//		return;
		//	}
		//}

		//ifnInfos = Arcadia.Util.ArrayAppend(
		//	ifnInfos_,
		//	new IFnInfo(key, f));
	}

	//public void AddFunctions (IFnInfo[] newIfnInfos)
	//{
	//	var newKeys = new HashSet<System.Object>(newIfnInfos.Select(inf => inf.key));

	//	ifnInfos = ifnInfos
	//		.Where(inf => !newKeys.Contains(inf.key))
	//		.Concat(newIfnInfos)
	//		.ToArray();
	//}


	// TODO: determine whether this should ever happen without the component detaching	
	public void RemoveAllFunctions ()
	{
		ifnInfos = new IFnInfo[0];
	}

	public void RemoveFunction (Keyword key)
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

	public void RealizeVars ()
	{
		// keyNames won't be there yet for fresh object
		// TODO: might want to clear them out after realizing vars
		if (keyNames != null) {
			ifnInfos = new IFnInfo[keyNames.Length];

			for (int i = 0; i < keyNames.Length; i++) {
				var kn = keyNames[i];
				var vn = varNames[i];
				Keyword k = Keyword.intern(kn); // TODO might be slightly wasteful, check this
				Symbol vsym = Symbol.intern(vn);
				Arcadia.Util.require(vsym.Namespace);
				Var v = RT.var(vsym.Namespace, vsym.Name);
				ifnInfos[i] = new IFnInfo(k, v);
			}
		}
	}

	public void FullInit ()
	{
		if (_fullyInitialized)
			return;

		//Init();
		InitializeOwnVars();
		RealizeVars();
		// deserialization:
		// hookStateDeserializeFn.invoke(this);
		arcadiaState = GetComponent<ArcadiaState>(); // not sure this should be here
		arcadiaState.Initialize();
		//RealizeAll(arcadiaState.state);
		// requireVarNamespacesFn.invoke(this);

		_fullyInitialized = true;
		_go = gameObject;

	}

	// ============================================================
	// messages

	public virtual void Awake ()
	{
		FullInit();
	}

	// ============================================================
	// Errors

	// TODO would make more sense to wrap thrown exception in another that carries this information
	public void PrintContext (int infInx)
	{
		var inf = ifnInfos_[infInx];
		Debug.LogError("Context: key: " + inf.key + "; fn: " + inf.fn + "; GameObject: " + _go.name + "; GameObject id: " + _go.GetInstanceID(), _go);
	}

	// ============================================================
	// RunFunctions

	// TODO: are any messages _nested_??? fortunately a miss shouldn't be catastrophic
	public void RunFunctions ()
	{
		if (!_fullyInitialized)
			FullInit();
		HookStateSystem.SetState(arcadiaState, _go, ifnInfos);
		int i = 0;
		try {
			for (; i < ifnInfos.Length; i++) {
				HookStateSystem.ifnInfoIndex = i;
				IFn f = ifnInfos[i].fn;
				var v = f as Var;
				if (v != null) {
					((IFn)v.getRawRoot()).invoke(_go, ifnInfos[i].key);
				} else {
					f.invoke(_go, ifnInfos[i].key);
				}
			}
		} catch (System.Exception) {
			PrintContext(i);
			throw;
		} finally {
			HookStateSystem.Clear();
		}
	}

	public void RunFunctions (object arg1)
	{
		if (!_fullyInitialized)
			FullInit();
		HookStateSystem.SetState(arcadiaState, _go, ifnInfos);
		int i = 0;
		try {
			for (; i < ifnInfos.Length; i++) {
				HookStateSystem.ifnInfoIndex = i;
				IFn f = ifnInfos[i].fn;
				var v = f as Var;
				if (v != null) {
					((IFn)v.getRawRoot()).invoke(_go, arg1, ifnInfos[i].key);
				} else {
					f.invoke(_go, arg1, ifnInfos[i].key);
				}
			}
		} catch (System.Exception) {
			PrintContext(i);
			throw;
		} finally {
			HookStateSystem.Clear();
		}
	}

	public void RunFunctions (object arg1, object arg2)
	{
		if (!_fullyInitialized)
			FullInit();
		HookStateSystem.SetState(arcadiaState, _go, ifnInfos);
		int i = 0;
		try {
			for (; i < ifnInfos.Length; i++) {
				HookStateSystem.ifnInfoIndex = i;
				IFn f = ifnInfos[i].fn;
				var v = f as Var;
				if (v != null) {
					((IFn)v.getRawRoot()).invoke(_go, arg1, arg2, ifnInfos[i].key);
				} else {
					f.invoke(_go, arg1, arg2, ifnInfos[i].key);
				}
			}
		} catch (System.Exception) {
			PrintContext(i);
			throw;
		} finally {
			HookStateSystem.Clear();
		}
	}

	public void RunFunctions (object arg1, object arg2, object arg3)
	{
		if (!_fullyInitialized)
			FullInit();
		HookStateSystem.SetState(arcadiaState, _go, ifnInfos);
		int i = 0;
		try {
			for (; i < ifnInfos.Length; i++) {
				HookStateSystem.ifnInfoIndex = i;
				IFn f = ifnInfos[i].fn;
				var v = f as Var;
				if (v != null) {
					((IFn)v.getRawRoot()).invoke(_go, arg1, arg2, arg3, ifnInfos[i].key);
				} else {
					f.invoke(_go, arg1, arg2, arg3, ifnInfos[i].key);
				}
			}
		} catch (System.Exception) {
			PrintContext(i);
			throw;
		} finally {
			HookStateSystem.Clear();
		}
	}

}
#endif