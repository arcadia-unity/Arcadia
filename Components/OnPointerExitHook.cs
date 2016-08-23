using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerExitHook : ArcadiaBehaviour, IPointerExitHandler   
{
  public void OnPointerExit(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}