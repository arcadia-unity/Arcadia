using UnityEngine;
using clojure.lang;

public class OnTriggerExit2DHook : ArcadiaBehaviour   
{
  public void OnTriggerExit2D(UnityEngine.Collider2D a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}