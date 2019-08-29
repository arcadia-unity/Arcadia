using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour
{
  public void OnMouseEnter()
  {
      RunFunctions();
  }
}