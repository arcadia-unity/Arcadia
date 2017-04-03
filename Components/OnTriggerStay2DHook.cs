using UnityEngine;
using clojure.lang;

public class OnTriggerStay2DHook : ArcadiaBehaviour   
{
  public void OnTriggerStay2D(UnityEngine.Collider2D a)
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