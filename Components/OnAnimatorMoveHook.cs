using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnAnimatorMoveHook : ArcadiaBehaviour
{
  public void OnAnimatorMove()
  {
      RunFunctions();
  }
}