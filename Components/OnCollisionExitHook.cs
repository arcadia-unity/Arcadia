using UnityEngine;
using clojure.lang;

public class OnCollisionExitHook : ArcadiaBehaviour   
{
  public void OnCollisionExit(UnityEngine.Collision a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}