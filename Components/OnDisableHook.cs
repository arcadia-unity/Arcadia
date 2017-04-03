using UnityEngine;
using clojure.lang;

public class OnDisableHook : ArcadiaBehaviour   
{
  public void OnDisable()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}