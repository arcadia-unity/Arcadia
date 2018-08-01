#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDeselectHook : ArcadiaBehaviour, IDeselectHandler
{
  public void OnDeselect(BaseEventData a)
  {
      RunFunctions(a);
  }
}
#endif