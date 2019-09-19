using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPreRenderHook : ArcadiaBehaviour
{
  public void OnPreRender()
  {
      RunFunctions();
  }
}