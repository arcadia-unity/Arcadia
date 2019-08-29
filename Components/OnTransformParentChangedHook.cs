using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnTransformParentChangedHook : ArcadiaBehaviour
{
  public void OnTransformParentChanged()
  {
      RunFunctions();
  }
}