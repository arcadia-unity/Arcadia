using UnityEngine;
using clojure.lang;

public class OnCollisionEnterHook : ArcadiaBehaviour   
{
  public void OnCollisionEnter(UnityEngine.Collision a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}