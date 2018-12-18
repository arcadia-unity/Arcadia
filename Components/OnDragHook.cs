#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDragHook : ArcadiaBehaviour, IDragHandler
{
  public void OnDrag(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif