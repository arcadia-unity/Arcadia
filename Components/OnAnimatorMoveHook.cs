using UnityEngine;
using clojure.lang;

public class OnAnimatorMoveHook : ArcadiaBehaviour   
{
  public void OnAnimatorMove()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}