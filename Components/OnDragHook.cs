using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDragHook : ArcadiaBehaviour, IDragHandler   
{
  public void OnDrag(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}