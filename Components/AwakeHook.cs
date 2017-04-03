using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour   
{
  public override void Awake()
  {
      base.Awake();
      var _go = gameObject;
      for (int i = 0; i < fns.Length; i++){
        var fn = fns[i];
        if (fn != null){
          fn.invoke(_go);
        } else {
          Debug.LogException(new System.Exception("Unresolved var: #'"+qualifiedVarNames[i]));
        }
      }
  }
}