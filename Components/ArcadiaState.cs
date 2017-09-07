﻿using UnityEngine;
using System.Collections;
using clojure.lang;

public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
  // TODO sorted maps?
  public string edn = "{}";
  public Atom state = new Atom(PersistentHashMap.EMPTY);
  
  public Atom objectDatabase = null;
  public int[] objectDatabaseIds = new int[0];
  public Object[] objectDatabaseObjects = new Object[0];
  
  private static IFn prStr = null;
  private static IFn readString = null;
  private static IFn requireFn = null;
  
  // creates objectDatabase atom from
  // objectDatabaseIds and objectDatabaseObjects
  public void BuildDatabaseAtom(bool force=false) {
    if(objectDatabase == null || force) {
      var idsToObjectsMap = PersistentHashMap.EMPTY;
      
      if(objectDatabaseIds.Length > 0 && objectDatabaseObjects.Length > 0) {
        // TODO transients?
        int len = System.Math.Min(objectDatabaseIds.Length, objectDatabaseObjects.Length);
        for(int i=0; i<len; i++) {
          idsToObjectsMap = (PersistentHashMap)idsToObjectsMap.assoc(objectDatabaseIds[i], objectDatabaseObjects[i]);
        }
      }
      
      objectDatabase = new Atom(idsToObjectsMap);
    }
  }
  
  void WipeDatabase() {
    objectDatabase = new Atom(PersistentHashMap.EMPTY);
  }
  
	private static Var dataReaders;

	private static Var awakeFn;

	private static void require (string s)
	{
		if (requireFn == null) {
			requireFn = RT.var("clojure.core", "require");
		}
		requireFn.invoke(Symbol.intern(s));
	}

	private static void initializeVars ()
	{
		string nsStr = "arcadia.literals";
		require(nsStr);
		if (dataReaders == null)
			dataReaders = RT.var(nsStr, "*data-readers*");
		string nsStr2 = "arcadia.internal.state-help";
		require(nsStr2);
		if (awakeFn == null)
			awakeFn = RT.var(nsStr2, "awake");
	}

	public void Awake ()
	{
		initializeVars();
		awakeFn.invoke(this);
	}


	public void OnBeforeSerialize()
  {
    if(prStr == null) prStr = (IFn)RT.var("clojure.core", "pr-str");
    if(requireFn == null) requireFn = (IFn)RT.var("clojure.core", "require");
    requireFn.invoke(Symbol.intern("arcadia.literals"));
    Namespace ArcadiaLiteralsNamespace = Namespace.findOrCreate(Symbol.intern("arcadia.literals"));
    Var ObjectDbVar = Var.intern(ArcadiaLiteralsNamespace, Symbol.intern("*object-db*")).setDynamic();

		initializeVars();

    WipeDatabase();
    Var.pushThreadBindings(RT.map(ObjectDbVar, objectDatabase));
    try {
      edn = (string)prStr.invoke(state.deref()); // side effects, updating objectDatabase
      var map = (PersistentHashMap)objectDatabase.deref();
      objectDatabaseIds  = (int[])RT.seqToTypedArray(typeof(int), RT.keys(map));
      objectDatabaseObjects = (Object[])RT.seqToTypedArray(typeof(Object), RT.vals(map));
    } finally {
      Var.popThreadBindings();
    }
  }
  
  public void OnAfterDeserialize()
  {
#if UNITY_EDITOR  
    Awake();
#endif
  }
}