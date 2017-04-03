using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBeginDragHook : ArcadiaBehaviour, IBeginDragHandler   
{
  public void OnBeginDrag(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}