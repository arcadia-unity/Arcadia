using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMouseDownHook : ArcadiaBehaviour
{
  public void OnMouseDown()
  {
      RunFunctions();
  }
}