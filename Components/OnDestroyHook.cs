#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnDestroyHook : ArcadiaBehaviour
{
  public void OnDestroy()
  {
      RunFunctions();
  }
}
#endif