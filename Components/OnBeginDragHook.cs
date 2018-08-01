#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBeginDragHook : ArcadiaBehaviour, IBeginDragHandler
{
  public void OnBeginDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif