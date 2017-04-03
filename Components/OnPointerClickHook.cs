using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerClickHook : ArcadiaBehaviour, IPointerClickHandler   
{
  public void OnPointerClick(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}