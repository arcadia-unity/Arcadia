using UnityEngine;
using clojure.lang;

public class OnEnableHook : ArcadiaBehaviour   
{
  public void OnEnable()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}