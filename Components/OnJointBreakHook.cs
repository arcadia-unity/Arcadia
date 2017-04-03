using UnityEngine;
using clojure.lang;

public class OnJointBreakHook : ArcadiaBehaviour   
{
  public void OnJointBreak(System.Single a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}