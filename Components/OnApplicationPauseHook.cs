using UnityEngine;
using clojure.lang;

public class OnApplicationPauseHook : ArcadiaBehaviour   
{
  public void OnApplicationPause(System.Boolean a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}