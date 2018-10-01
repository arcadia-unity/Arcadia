#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnScrollHook : ArcadiaBehaviour, IScrollHandler
{
  public void OnScroll(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif