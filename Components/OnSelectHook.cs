using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSelectHook : ArcadiaBehaviour, ISelectHandler   
{
  public void OnSelect(BaseEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}