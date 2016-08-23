using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCancelHook : ArcadiaBehaviour, ICancelHandler   
{
  public void OnCancel(BaseEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}