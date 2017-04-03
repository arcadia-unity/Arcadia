using UnityEngine;
using clojure.lang;

public class OnFailedToConnectToMasterServerHook : ArcadiaBehaviour   
{
  public void OnFailedToConnectToMasterServer(UnityEngine.NetworkConnectionError a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}