#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerDownHook : ArcadiaBehaviour, IPointerDownHandler
{
  public void OnPointerDown(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif