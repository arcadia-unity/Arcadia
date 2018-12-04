#if NET_4_6
using UnityEngine;
using System;
using System.Collections.Generic;
using System.Collections.Specialized;
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

	[System.NonSerialized]
	public bool isBuilding = false; // flag for faster building during roles+

	// a sorted list should be available for fast conj'ing deduplicated by some sortable criterion such as keys
	// or just a dictionary, simpler
	public Dictionary<object, IFn> buildingIfnInfos;

	public bool fullyInitialized {
		get {
			return _fullyInitialized;
		}
	}

	public struct IFnInfo
	{
		public object key;
		public IFn fn;
		// cached lookup fields
		public int cacheSize;
		public JumpMap.KeyVal kv1;
		public JumpMap.KeyVal kv2;
		public JumpMap.KeyVal kv3;
		public JumpMap.KeyVal kv4;

		public IFnInfo (object key_, IFn fn_, int cacheSize_, JumpMap.KeyVal kv1_, JumpMap.KeyVal kv2_, JumpMap.KeyVal kv3_, JumpMap.KeyVal kv4_)
		{
			key = key_;
			fn = fn_;
			cacheSize = cacheSize_;
			kv1 = kv1_;
			kv2 = kv2_;
			kv3 = kv3_;
			kv4 = kv4_;
		}

		public IFnInfo (object key_, IFn fn_)
		{
			key = key_;
			fn = fn_;
			cacheSize = 0;
			kv1 = null;
			kv2 = null;
			kv3 = null;
			kv4 = null;
		}

		// don't think we need this
		public IFnInfo Refreshed ()
		{
			switch (cacheSize) {
			case 0:
				return this;
			case 1:
				return new IFnInfo(key, fn, cacheSize, kv1.Refreshed(), null, null, null);
			case 2:
				return new IFnInfo(key, fn, cacheSize, kv1.Refreshed(), kv2.Refreshed(), null, null);
			case 3:
				return new IFnInfo(key, fn, cacheSize, kv1.Refreshed(), kv2.Refreshed(), kv3.Refreshed(), null);
			case 4:
				return new IFnInfo(key, fn, cacheSize, kv1.Refreshed(), kv2.Refreshed(), kv3.Refreshed(), kv4.Refreshed());
			default:
				throw new InvalidOperationException("Size of IfnInfo out of bounds");
			}
		}

		public IFnInfo RemoveKey (object k)
		{
			for (int i = 1; i <= cacheSize; i++) {
				switch (i) {
				case 1:
					if (kv1.key == k)
						return new IFnInfo(key, fn, cacheSize - 1, kv2, kv3, kv4, null);
					break;
				case 2:
					if (kv2.key == k)
						return new IFnInfo(key, fn, cacheSize - 1, kv1, kv3, kv4, null);
					break;
				case 3:
					if (kv3.key == k)
						return new IFnInfo(key, fn, cacheSize - 1, kv1, kv2, kv4, null);
					break;
				case 4:
					if (kv4.key == k)
						return new IFnInfo(key, fn, cacheSize - 1, kv1, kv2, kv3, null);
					break;
				default:
					break;
				}
			}
			return this;
		}

		public bool TryGetVal (object k, out object val)
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
				throw new System.InvalidOperationException("Size of IfnInfo out of bounds");
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
			Keyword k = (Keyword)inf.key;
			keyNames[i] = k != null ? k.Namespace + "/" + k.Name : k.Name;
			Var v = inf.fn as Var;
			if (v != null) {
				// TODO: come back to this after Var rewrite
				varNames[i] = v.Namespace.Name + "/" + v.Symbol.Name;
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
	// TODO fix where this is

	// not sure we need the property thing. but it should get inlined anyway
	private IFnInfo[] ifnInfos_ = new IFnInfo[0]; // might not need to initialize this to empty?

	// maybe this should be NonSerialized
	// [System.NonSerialized]
	public ArcadiaState arcadiaState;

	// compute indexes lazily
	//private IPersistentMap indexes_;

	public IFnInfo[] ifnInfos {
		get {
			return ifnInfos_;
		}
		set {
			ifnInfos_ = value;
			//InvalidateIndexes();
		}
	}

	// used in arcadia.core
	public IFn CallbackForKey (object key)
	{
		if (ifnInfos_ != null) {
			foreach (var inf in ifnInfos_) {
				if (inf.key == key)
					return inf.fn;
			}
		}
		return null;
	}

	//public object[] keys {
	//	get {
	//		var arr = new object[ifnInfos_.Length];
	//		for (int i = 0; i < ifnInfos_.Length; i++) {
	//			arr[i] = ifnInfos_[i].key;
	//		}
	//		return arr;
	//	}
	//}

	//public IFn[] fns {
	//	get {
	//		var arr = new IFn[ifnInfos_.Length];
	//		for (int i = 0; i < ifnInfos_.Length; i++) {
	//			arr[i] = ifnInfos_[i].fn;
	//		}
	//		return arr;
	//	}
	//}

	//public IPersistentMap indexes {
	//	get {
	//		if (indexes_ == null)
	//			indexes_ = Arcadia.Util.Zipmap(keys, fns);
	//		return indexes_;
	//	}
	//}

	// ============================================================

	//public void InvalidateIndexes ()
	//{
	//	indexes_ = null;
	//}

	// These are for faster roles+ (otherwise n^2)
	// TODO: consider using an initial capacity
	// really this might be a good place for a transient
	public void StartBuild ()
	{
		isBuilding = true;
		// TODO COULD have this just hanging around from the last time
		// might not be worth it, not sure how many objects get multiple
		// roles+ in their lifetimes
		buildingIfnInfos = new Dictionary<object, IFn>();
		foreach (IFnInfo inf in ifnInfos_) {
			buildingIfnInfos.Add(inf.key, inf.fn);
		}
	}

	public void CompleteBuild ()
	{
		isBuilding = false;
		ifnInfos_ = new IFnInfo[buildingIfnInfos.Count];
		int i = 0;
		foreach (var e in buildingIfnInfos) {
			ifnInfos_[i] = new IFnInfo(e.Key, e.Value);
			i++;
		}
		// or could clear it
		buildingIfnInfos = null;
		//InvalidateIndexes();
	}

	public void AddFunction (Keyword key, IFn f)
	{
		FullInit();
		if (isBuilding) {
			buildingIfnInfos.Add(key, f);
		} else {
			// just treat it like an associative array for now
			for (int i = 0; i < ifnInfos.Length; i++) {
				if (ifnInfos[i].key == key) {
					ifnInfos[i] = new IFnInfo(key, f);
					//InvalidateIndexes();
					return;
				}
			}
			ifnInfos = Arcadia.Util.ArrayAppend(ifnInfos, new IFnInfo(key, f));
			//InvalidateIndexes();
		}
	}

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
				Keyword k = Keyword.intern(kn);
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

		RealizeVars();
		arcadiaState = GetComponent<ArcadiaState>(); // not sure this should be here
		arcadiaState.Initialize();
		_fullyInitialized = true;
		_go = gameObject;

	}

	// ============================================================
	// RemoveKey

	public void RemoveCachedKey (object k)
	{
		if (ifnInfos == null)
			return;

		for (int i = 0; i < ifnInfos_.Length; i++) {
			ifnInfos_[i] = ifnInfos_[i].RemoveKey(k);
		}

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
				Arcadia.Util.AsIFn(ifnInfos[i].fn).invoke(_go, ifnInfos[i].key);
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
				Arcadia.Util.AsIFn(ifnInfos[i].fn).invoke(_go, ifnInfos[i].key, arg1);
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
				Arcadia.Util.AsIFn(ifnInfos[i].fn).invoke(_go, ifnInfos[i].key, arg1, arg2);
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
				Arcadia.Util.AsIFn(ifnInfos[i].fn).invoke(_go, ifnInfos[i].key, arg1, arg2, arg3);
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