using UnityEngine;
using clojure.lang;

public class OnCollisionExit2DHook : ArcadiaBehaviour   
{
  public void OnCollisionExit2D(UnityEngine.Collision2D a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}