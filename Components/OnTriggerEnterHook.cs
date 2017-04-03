using UnityEngine;
using clojure.lang;

public class OnTriggerEnterHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter(UnityEngine.Collider a)
  {
      var _go = gameObject;
      for (int i = 0; i < fns.Length; i++){
        var fn = fns[i];
        if (fn != null){
          fn.invoke(_go, a);
        } else {
          Debug.LogException(new System.Exception("Unresolved var: #'"+qualifiedVarNames[i]));
        }
      }
  }
}