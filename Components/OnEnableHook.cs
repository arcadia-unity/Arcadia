#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnEnableHook : ArcadiaBehaviour
{
  public void OnEnable()
  {
      RunFunctions();
  }
}
#endif