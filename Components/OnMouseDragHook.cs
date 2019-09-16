using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMouseDragHook : ArcadiaBehaviour
{
  public void OnMouseDrag()
  {
      RunFunctions();
  }
}