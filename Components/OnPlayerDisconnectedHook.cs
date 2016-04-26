using UnityEngine;
using clojure.lang;

public class OnPlayerDisconnectedHook : ArcadiaBehaviour   
{
  public void OnPlayerDisconnected(UnityEngine.NetworkPlayer a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}