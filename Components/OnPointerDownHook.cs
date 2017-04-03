using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerDownHook : ArcadiaBehaviour, IPointerDownHandler   
{
  public void OnPointerDown(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}