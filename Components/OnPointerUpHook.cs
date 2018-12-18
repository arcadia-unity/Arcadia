#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerUpHook : ArcadiaBehaviour, IPointerUpHandler
{
  public void OnPointerUp(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif