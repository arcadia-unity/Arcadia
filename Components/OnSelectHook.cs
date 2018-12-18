#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSelectHook : ArcadiaBehaviour, ISelectHandler
{
  public void OnSelect(BaseEventData a)
  {
      RunFunctions(a);
  }
}
#endif