using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnControllerColliderHitHook : ArcadiaBehaviour
{
  public void OnControllerColliderHit(UnityEngine.ControllerColliderHit a)
  {
      RunFunctions(a);
  }
}