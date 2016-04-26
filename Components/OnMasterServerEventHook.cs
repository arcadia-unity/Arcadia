using UnityEngine;
using clojure.lang;

public class OnMasterServerEventHook : ArcadiaBehaviour   
{
  public void OnMasterServerEvent(UnityEngine.MasterServerEvent a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}