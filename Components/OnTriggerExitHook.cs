using UnityEngine;
using clojure.lang;

public class OnTriggerExitHook : ArcadiaBehaviour   
{
  public void OnTriggerExit(UnityEngine.Collider a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}