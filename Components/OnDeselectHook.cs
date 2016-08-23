using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDeselectHook : ArcadiaBehaviour, IDeselectHandler   
{
  public void OnDeselect(BaseEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}