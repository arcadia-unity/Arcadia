#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnServerInitializedHook : ArcadiaBehaviour
{
  public void OnServerInitialized()
  {
      RunFunctions();
  }
}
#endif