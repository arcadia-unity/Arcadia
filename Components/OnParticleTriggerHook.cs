#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnParticleTriggerHook : ArcadiaBehaviour
{
  public void OnParticleTrigger()
  {
      RunFunctions();
  }
}
#endif