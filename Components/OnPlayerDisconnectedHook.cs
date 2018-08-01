#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnPlayerDisconnectedHook : ArcadiaBehaviour
{
  public void OnPlayerDisconnected(UnityEngine.NetworkPlayer a)
  {
      RunFunctions(a);
  }
}
#endif