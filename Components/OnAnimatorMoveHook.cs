using UnityEngine;
using clojure.lang;

public class OnAnimatorMoveHook : ArcadiaBehaviour
{
  void OnAnimatorMove()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}