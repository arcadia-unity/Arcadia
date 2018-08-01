#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnBecameVisibleHook : ArcadiaBehaviour
{
  public void OnBecameVisible()
  {
      RunFunctions();
  }
}
#endif