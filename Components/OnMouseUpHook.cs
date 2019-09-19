using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour
{
  public void OnMouseUp()
  {
      RunFunctions();
  }
}