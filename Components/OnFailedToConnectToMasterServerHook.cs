using UnityEngine;
using clojure.lang;

public class OnFailedToConnectToMasterServerHook : ArcadiaBehaviour   
{
  public void OnFailedToConnectToMasterServer(UnityEngine.NetworkConnectionError a)
  {

  	RunFunctions(a);

  }
}