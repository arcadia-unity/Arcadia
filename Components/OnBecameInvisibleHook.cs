#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnBecameInvisibleHook : ArcadiaBehaviour
{
  public void OnBecameInvisible()
  {
      RunFunctions();
  }
}
#endif