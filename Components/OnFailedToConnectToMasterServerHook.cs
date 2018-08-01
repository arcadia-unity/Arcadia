#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnFailedToConnectToMasterServerHook : ArcadiaBehaviour
{
  public void OnFailedToConnectToMasterServer(UnityEngine.NetworkConnectionError a)
  {
      RunFunctions(a);
  }
}
#endif