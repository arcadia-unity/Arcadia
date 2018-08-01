#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnPlayerConnectedHook : ArcadiaBehaviour
{
  public void OnPlayerConnected(UnityEngine.NetworkPlayer a)
  {
      RunFunctions(a);
  }
}
#endif