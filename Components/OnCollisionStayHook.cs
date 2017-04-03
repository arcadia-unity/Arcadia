using UnityEngine;
using clojure.lang;

public class OnCollisionStayHook : ArcadiaBehaviour   
{
  public void OnCollisionStay(UnityEngine.Collision a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}