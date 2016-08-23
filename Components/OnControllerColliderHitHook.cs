using UnityEngine;
using clojure.lang;

public class OnControllerColliderHitHook : ArcadiaBehaviour   
{
  public void OnControllerColliderHit(UnityEngine.ControllerColliderHit a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}