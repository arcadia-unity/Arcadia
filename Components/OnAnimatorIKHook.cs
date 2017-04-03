using UnityEngine;
using clojure.lang;

public class OnAnimatorIKHook : ArcadiaBehaviour   
{
  public void OnAnimatorIK(System.Int32 a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}