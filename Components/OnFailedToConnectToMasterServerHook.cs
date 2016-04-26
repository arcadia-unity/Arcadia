using UnityEngine;
using clojure.lang;

public class OnFailedToConnectToMasterServerHook : ArcadiaBehaviour   
{
  public void OnFailedToConnectToMasterServer(UnityEngine.NetworkConnectionError a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}