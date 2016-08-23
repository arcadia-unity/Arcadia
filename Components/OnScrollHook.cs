using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnScrollHook : ArcadiaBehaviour, IScrollHandler   
{
  public void OnScroll(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}