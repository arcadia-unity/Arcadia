#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCancelHook : ArcadiaBehaviour, ICancelHandler
{
  public void OnCancel(BaseEventData a)
  {
      RunFunctions(a);
  }
}
#endif