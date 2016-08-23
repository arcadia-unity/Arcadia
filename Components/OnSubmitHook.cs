using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSubmitHook : ArcadiaBehaviour, ISubmitHandler   
{
  public void OnSubmit(BaseEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}