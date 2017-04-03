using UnityEngine;
using clojure.lang;

public class OnDestroyHook : ArcadiaBehaviour   
{
  public void OnDestroy()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}