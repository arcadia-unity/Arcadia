using UnityEngine;
using clojure.lang;

public class OnBecameVisibleHook : ArcadiaBehaviour   
{
  public void OnBecameVisible()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}