using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDragHook : ArcadiaBehaviour
{
  public void OnDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}