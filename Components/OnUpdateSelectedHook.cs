#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnUpdateSelectedHook : ArcadiaBehaviour, IUpdateSelectedHandler
{
  public void OnUpdateSelected(BaseEventData a)
  {
      RunFunctions(a);
  }
}
#endif