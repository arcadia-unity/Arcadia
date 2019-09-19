using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnEndDragHook : ArcadiaBehaviour
{
  public void OnEndDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}