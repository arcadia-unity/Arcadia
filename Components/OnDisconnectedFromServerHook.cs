using UnityEngine;
using clojure.lang;

public class OnDisconnectedFromServerHook : ArcadiaBehaviour   
{
  public void OnDisconnectedFromServer(UnityEngine.NetworkDisconnection a)
  {

  	RunFunctions(a);

  }
}