using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerUpHook : ArcadiaBehaviour
{
  public void OnPointerUp(PointerEventData a)
  {
      RunFunctions(a);
  }
}