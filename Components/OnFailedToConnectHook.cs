using UnityEngine;
using clojure.lang;

public class OnFailedToConnectHook : ArcadiaBehaviour   
{
  public void OnFailedToConnect(UnityEngine.NetworkConnectionError a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}