using UnityEngine;
using System.Collections.Generic;
using clojure.lang;

public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
	IFn[] _fns = new IFn[0];
	public IPersistentMap fnIndexes = PersistentHashMap.create();

	public IFn[] fns
	{
		get { return _fns; }
		set
		{
			_fns = value;
			qualifiedVarNames = null;
			OnBeforeSerialize();
		}
	}

	[SerializeField]
	public List<string> qualifiedVarNames;

	// if fn is a var, store in serializedVar 
	public void OnBeforeSerialize()
	{
		List<string> newQualifiedVarNames = new List<string>(fns.Length);

		foreach (var f in fns)
		{
			Var v = f as Var;
			if (v != null)
			{
				newQualifiedVarNames.Add(v.Namespace.Name + "/" + v.Symbol.Name);
			}
		}

		qualifiedVarNames = newQualifiedVarNames;
	}

	public IFn[] AddFunction(IFn f)
	{
		return AddFunction(f, f);
	}

	public IFn[] AddFunction(IFn f, object key)
	{
		var fnList = new List<IFn>(fns);
		fnIndexes = fnIndexes.assoc(key, fnList.Count);
		fnList.Add(f);
		fns = fnList.ToArray();
		return fns;
	}

	public IFn[] RemoveAllFunctions()
	{
		var oldFns = fns;
		fns = new IFn[0];
		return oldFns;
	}

	public IFn RemoveFunction(object key)
	{
		var indexToRemove = fnIndexes.valAt(key);
		if (indexToRemove != null)
		{
			var i = (int)indexToRemove;
			var obj = fns[i];
			var fnList = new List<IFn>(fns);
			fnList.RemoveAt((int)indexToRemove);
			fns = fnList.ToArray();
			return obj;
		}
		else
		{
			return null;
		}
	}

	public void OnAfterDeserialize()
	{
#if UNITY_EDITOR
		Awake();
#endif
	}

	private static IFn requireFn = null;

	// if serializedVar not null, set fn to var
	public void Awake()
	{
		if (requireFn == null)
			requireFn = RT.var("clojure.core", "require");
		if (qualifiedVarNames != null)
		{
			List<IFn> fnList = new List<IFn>(qualifiedVarNames.Count);
			foreach (var varName in qualifiedVarNames)
			{
				if (varName != "")
				{
					Symbol sym = Symbol.intern(varName);
					if (sym.Namespace != null)
					{
						var nameSym = Symbol.intern(sym.Name);
						var nsSym = Symbol.intern(sym.Namespace);
						requireFn.invoke(nsSym);
						var v = Namespace.find(nsSym).FindInternedVar(nameSym);
						fnList.Add(v);
					}
				}
			}
			fns = fnList.ToArray();
		}
	}
}