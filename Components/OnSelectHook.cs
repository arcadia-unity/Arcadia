using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSelectHook : ArcadiaBehaviour
{
  public void OnSelect(BaseEventData a)
  {
      RunFunctions(a);
  }
}