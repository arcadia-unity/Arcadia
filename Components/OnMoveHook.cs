using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMoveHook : ArcadiaBehaviour, IMoveHandler   
{
  public void OnMove(AxisEventData a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}