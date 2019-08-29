using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerExitHook : ArcadiaBehaviour
{
  public void OnPointerExit(PointerEventData a)
  {
      RunFunctions(a);
  }
}