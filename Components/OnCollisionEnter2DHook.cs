using UnityEngine;
using clojure.lang;

public class OnCollisionEnter2DHook : ArcadiaBehaviour   
{
  public void OnCollisionEnter2D(UnityEngine.Collision2D a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}