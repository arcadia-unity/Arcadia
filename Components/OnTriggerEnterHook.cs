using UnityEngine;
using clojure.lang;

public class OnTriggerEnterHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter(UnityEngine.Collider a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}