using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnInitializePotentialDragHook : ArcadiaBehaviour, IInitializePotentialDragHandler   
{
  public void OnInitializePotentialDrag(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}