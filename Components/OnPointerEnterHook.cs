using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerEnterHook : ArcadiaBehaviour, IPointerEnterHandler   
{
  public void OnPointerEnter(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}