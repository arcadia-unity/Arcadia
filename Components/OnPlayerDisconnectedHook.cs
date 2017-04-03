using UnityEngine;
using clojure.lang;

public class OnPlayerDisconnectedHook : ArcadiaBehaviour   
{
  public void OnPlayerDisconnected(UnityEngine.NetworkPlayer a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}