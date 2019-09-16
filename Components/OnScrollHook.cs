using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnScrollHook : ArcadiaBehaviour
{
  public void OnScroll(PointerEventData a)
  {
      RunFunctions(a);
  }
}