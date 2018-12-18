#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerExitHook : ArcadiaBehaviour, IPointerExitHandler
{
  public void OnPointerExit(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif