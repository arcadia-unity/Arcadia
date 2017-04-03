using UnityEngine;
using clojure.lang;

public class OnPreCullHook : ArcadiaBehaviour   
{
  public void OnPreCull()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}