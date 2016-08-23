using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDropHook : ArcadiaBehaviour, IDropHandler   
{
  public void OnDrop(PointerEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}