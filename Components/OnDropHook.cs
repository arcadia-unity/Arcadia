#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDropHook : ArcadiaBehaviour, IDropHandler
{
  public void OnDrop(PointerEventData a)
  {
      RunFunctions(a);
  }
}
#endif