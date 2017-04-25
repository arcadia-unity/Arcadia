using UnityEngine;
using clojure.lang;

public class OnMasterServerEventHook : ArcadiaBehaviour   
{
  public void OnMasterServerEvent(UnityEngine.MasterServerEvent a)
  {

  	RunFunctions(a);

  }
}