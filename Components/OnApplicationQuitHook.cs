#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnApplicationQuitHook : ArcadiaBehaviour
{
  public void OnApplicationQuit()
  {
      RunFunctions();
  }
}
#endif