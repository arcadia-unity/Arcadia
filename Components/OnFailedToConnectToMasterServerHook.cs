using UnityEngine;
using clojure.lang;

public class OnFailedToConnectToMasterServerHook : ArcadiaBehaviour
{
  void OnFailedToConnectToMasterServer(UnityEngine.NetworkConnectionError G__18651)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18651);
  }
}