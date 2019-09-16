using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCancelHook : ArcadiaBehaviour
{
  public void OnCancel(BaseEventData a)
  {
      RunFunctions(a);
  }
}