using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBeginDragHook : ArcadiaBehaviour, IBeginDragHandler   
{
  public void OnBeginDrag(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}