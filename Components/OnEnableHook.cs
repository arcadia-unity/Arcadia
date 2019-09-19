using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnEnableHook : ArcadiaBehaviour
{
  public void OnEnable()
  {
      RunFunctions();
  }
}