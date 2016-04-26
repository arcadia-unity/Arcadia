using UnityEngine;
using clojure.lang;

public class OnControllerColliderHitHook : ArcadiaBehaviour   
{
  public void OnControllerColliderHit(UnityEngine.ControllerColliderHit a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}