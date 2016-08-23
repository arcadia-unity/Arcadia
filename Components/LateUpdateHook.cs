using UnityEngine;
using clojure.lang;

public class LateUpdateHook : ArcadiaBehaviour   
{
  public void LateUpdate()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}