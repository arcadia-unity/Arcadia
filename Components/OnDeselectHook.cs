using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDeselectHook : ArcadiaBehaviour
{
  public void OnDeselect(BaseEventData a)
  {
      RunFunctions(a);
  }
}