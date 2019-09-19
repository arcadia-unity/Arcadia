using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDisableHook : ArcadiaBehaviour
{
  public void OnDisable()
  {
      RunFunctions();
  }
}