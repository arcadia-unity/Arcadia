using UnityEngine;
using clojure.lang;

public class OnPlayerConnectedHook : ArcadiaBehaviour   
{
  public void OnPlayerConnected(UnityEngine.NetworkPlayer a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}