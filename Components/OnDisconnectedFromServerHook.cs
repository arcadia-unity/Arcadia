using UnityEngine;
using clojure.lang;

public class OnDisconnectedFromServerHook : ArcadiaBehaviour   
{
  public void OnDisconnectedFromServer(UnityEngine.NetworkDisconnection a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}