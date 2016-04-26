using UnityEngine;
using clojure.lang;

public class OnDisconnectedFromServerHook : ArcadiaBehaviour   
{
  public void OnDisconnectedFromServer(UnityEngine.NetworkDisconnection a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}