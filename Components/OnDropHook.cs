using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDropHook : ArcadiaBehaviour
{
  public void OnDrop(PointerEventData a)
  {
      RunFunctions(a);
  }
}