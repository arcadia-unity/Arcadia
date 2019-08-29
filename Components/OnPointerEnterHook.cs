using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerEnterHook : ArcadiaBehaviour
{
  public void OnPointerEnter(PointerEventData a)
  {
      RunFunctions(a);
  }
}