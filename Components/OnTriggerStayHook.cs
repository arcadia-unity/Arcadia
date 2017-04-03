using UnityEngine;
using clojure.lang;

public class OnTriggerStayHook : ArcadiaBehaviour   
{
  public void OnTriggerStay(UnityEngine.Collider a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}