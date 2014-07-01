using UnityEngine;
using System.Collections;
using clojure.lang;

public class ClojureDelegate : MonoBehaviour {
  public string nameSpace = "";
  public string prefix = "";

  Var updateFn;
  Var startFn;
  Var onMouseDownFn;
  // ... 

  // protected GetMethods(IPersistentMap map) {
  //   Var updateFn = map.entryAt(Keyword.intern("update"));
  // }

  void Start () {
   RT.load(nameSpace);
   Var fnVar = RT.var(nameSpace, prefix + "start");
   if(fnVar.isBound) fnVar.invoke(this);
  }
  
  void Update () {
   Var fnVar = RT.var(nameSpace, prefix + "update");
   if(fnVar.isBound) fnVar.invoke(this);
  }

  void FixedUpdate () {
   Var fnVar = RT.var(nameSpace, prefix + "FixedUpdate");
   if(fnVar.isBound) fnVar.invoke(this);
  }

  void OnMouseDown () {
   Var fnVar = RT.var(nameSpace, prefix + "on-mouse-down");
   if(fnVar.isBound) fnVar.invoke(this);
  }

  // etc
}