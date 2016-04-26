using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMoveHook : ArcadiaBehaviour, IMoveHandler   
{
  public void OnMove(AxisEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}