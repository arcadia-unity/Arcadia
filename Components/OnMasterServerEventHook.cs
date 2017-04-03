using UnityEngine;
using clojure.lang;

public class OnMasterServerEventHook : ArcadiaBehaviour   
{
  public void OnMasterServerEvent(UnityEngine.MasterServerEvent a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}