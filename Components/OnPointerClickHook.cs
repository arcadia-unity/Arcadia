using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerClickHook : ArcadiaBehaviour
{
  public void OnPointerClick(PointerEventData a)
  {
      RunFunctions(a);
  }
}