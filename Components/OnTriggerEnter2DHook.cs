using UnityEngine;
using clojure.lang;

public class OnTriggerEnter2DHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter2D(UnityEngine.Collider2D a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}