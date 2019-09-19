using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMouseExitHook : ArcadiaBehaviour
{
  public void OnMouseExit()
  {
      RunFunctions();
  }
}