using UnityEngine;
using clojure.lang;

public class OnControllerColliderHitHook : ArcadiaBehaviour
{
  void OnControllerColliderHit(UnityEngine.ControllerColliderHit G__18657)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18657);
  }
}