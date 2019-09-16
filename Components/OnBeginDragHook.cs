using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBeginDragHook : ArcadiaBehaviour
{
  public void OnBeginDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}