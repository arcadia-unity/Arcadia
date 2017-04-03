using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerUpHook : ArcadiaBehaviour, IPointerUpHandler   
{
  public void OnPointerUp(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}