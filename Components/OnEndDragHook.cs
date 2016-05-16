using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnEndDragHook : ArcadiaBehaviour, IEndDragHandler   
{
  public void OnEndDrag(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}