using UnityEngine;
using clojure.lang;

public class OnBecameInvisibleHook : ArcadiaBehaviour   
{
  public void OnBecameInvisible()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}