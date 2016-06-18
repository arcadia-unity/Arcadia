using UnityEngine;
using System.Collections;
using clojure.lang;

public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
  // TODO sorted maps?
  public string edn = "{}";
  public Atom state = new Atom(PersistentHashMap.EMPTY);
  
  public Atom objectDatabase;
  public int[] objectDatabaseIds = new int[0];
  public Object[] objectDatabaseObjects = new Object[0];
  
  static IFn prStr;
  static IFn readString;
  
  // creates objectDatabase atom from
  // objectDatabaseIds and objectDatabaseObjects
  public void BuildDatabaseAtom(bool force=false) {
    if(objectDatabase == null || force) {
      Debug.Log("ArcadiaState BuildDatabaseAtom");
      RT.var("clojure.core", "require").invoke(Symbol.intern("arcadia.literals"));
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
  
  static ArcadiaState()
  {
    prStr = (IFn)RT.var("clojure.core", "pr-str");
    readString = (IFn)RT.var("clojure.core", "read-string");
  }
    
  public void OnBeforeSerialize()
  {
    Namespace ArcadiaLiteralsNamespace = Namespace.findOrCreate(Symbol.intern("arcadia.literals"));
    Var ObjectDbVar = Var.intern(ArcadiaLiteralsNamespace, Symbol.intern("*object-db*")).setDynamic();
    
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
    Namespace ArcadiaLiteralsNamespace = Namespace.findOrCreate(Symbol.intern("arcadia.literals"));
    Var ObjectDbVar = Var.intern(ArcadiaLiteralsNamespace, Symbol.intern("*object-db*")).setDynamic();

    BuildDatabaseAtom();
    Var.pushThreadBindings(RT.map(ObjectDbVar, objectDatabase));
    try {
      state = new Atom(readString.invoke(edn));
    } finally {
      Var.popThreadBindings();
    }
  }
}