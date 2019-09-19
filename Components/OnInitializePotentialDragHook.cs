using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnInitializePotentialDragHook : ArcadiaBehaviour
{
  public void OnInitializePotentialDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}