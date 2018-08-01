#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMoveHook : ArcadiaBehaviour, IMoveHandler
{
  public void OnMove(AxisEventData a)
  {
      RunFunctions(a);
  }
}
#endif