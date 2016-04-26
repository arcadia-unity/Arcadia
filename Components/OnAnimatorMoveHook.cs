using UnityEngine;
using clojure.lang;

public class OnAnimatorMoveHook : ArcadiaBehaviour   
{
  public void OnAnimatorMove()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}