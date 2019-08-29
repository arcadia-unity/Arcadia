using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnParticleTriggerHook : ArcadiaBehaviour
{
  public void OnParticleTrigger()
  {
      RunFunctions();
  }
}