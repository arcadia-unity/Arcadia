#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnConnectedToServerHook : ArcadiaBehaviour
{
  public void OnConnectedToServer()
  {
      RunFunctions();
  }
}
#endif