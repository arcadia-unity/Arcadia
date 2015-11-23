using UnityEngine;
using clojure.lang;

public class OnMasterServerEventHook : ArcadiaBehaviour
{
  void OnMasterServerEvent(UnityEngine.MasterServerEvent G__18670)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18670);
  }
}