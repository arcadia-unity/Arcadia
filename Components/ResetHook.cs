#if NET_4_6
using UnityEngine;
using clojure.lang;

public class ResetHook : ArcadiaBehaviour
{
  public void Reset()
  {
      RunFunctions();
  }
}
#endif