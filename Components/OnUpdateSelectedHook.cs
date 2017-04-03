using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnUpdateSelectedHook : ArcadiaBehaviour, IUpdateSelectedHandler   
{
  public void OnUpdateSelected(BaseEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}