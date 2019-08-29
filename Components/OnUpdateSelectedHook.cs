using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnUpdateSelectedHook : ArcadiaBehaviour
{
  public void OnUpdateSelected(BaseEventData a)
  {
      RunFunctions(a);
  }
}