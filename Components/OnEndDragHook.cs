using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnEndDragHook : ArcadiaBehaviour, IEndDragHandler   
{
  public void OnEndDrag(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}