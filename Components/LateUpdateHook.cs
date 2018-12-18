#if NET_4_6
using UnityEngine;
using clojure.lang;

public class LateUpdateHook : ArcadiaBehaviour
{
  public void LateUpdate()
  {
      RunFunctions();
  }
}
#endif