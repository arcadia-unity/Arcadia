using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnTransformChildrenChangedHook : ArcadiaBehaviour
{
  public void OnTransformChildrenChanged()
  {
      RunFunctions();
  }
}