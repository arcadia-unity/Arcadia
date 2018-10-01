#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnInitializePotentialDragHook : ArcadiaBehaviour, IInitializePotentialDragHandler
{
  public void OnInitializePotentialDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif