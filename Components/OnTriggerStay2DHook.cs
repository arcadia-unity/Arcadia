using UnityEngine;
using clojure.lang;

public class OnTriggerStay2DHook : ArcadiaBehaviour   
{
  public void OnTriggerStay2D(UnityEngine.Collider2D a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}