using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour
{
  public void OnWillRenderObject()
  {
      RunFunctions();
  }
}