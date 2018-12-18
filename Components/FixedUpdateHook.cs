#if NET_4_6
using UnityEngine;
using clojure.lang;

public class FixedUpdateHook : ArcadiaBehaviour
{
  public void FixedUpdate()
  {
      RunFunctions();
  }
}
#endif