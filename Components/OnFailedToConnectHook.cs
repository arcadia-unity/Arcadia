using UnityEngine;
using clojure.lang;

public class OnFailedToConnectHook : ArcadiaBehaviour   
{
  public void OnFailedToConnect(UnityEngine.NetworkConnectionError a)
  {

  	RunFunctions(a);

  }
}