using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnRenderObjectHook : ArcadiaBehaviour
{
  public void OnRenderObject()
  {
      RunFunctions();
  }
}