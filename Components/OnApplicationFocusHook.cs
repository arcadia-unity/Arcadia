using UnityEngine;
using clojure.lang;

public class OnApplicationFocusHook : ArcadiaBehaviour   
{
  public void OnApplicationFocus(System.Boolean a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}