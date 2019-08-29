using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSubmitHook : ArcadiaBehaviour
{
  public void OnSubmit(BaseEventData a)
  {
      RunFunctions(a);
  }
}