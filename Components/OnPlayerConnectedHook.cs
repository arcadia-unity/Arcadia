using UnityEngine;
using clojure.lang;

public class OnPlayerConnectedHook : ArcadiaBehaviour   
{
  public void OnPlayerConnected(UnityEngine.NetworkPlayer a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}