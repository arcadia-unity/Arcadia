using UnityEngine;
using clojure.lang;

public class OnFailedToConnectHook : ArcadiaBehaviour   
{
  public void OnFailedToConnect(UnityEngine.NetworkConnectionError a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}