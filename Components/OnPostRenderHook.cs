using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPostRenderHook : ArcadiaBehaviour
{
  public void OnPostRender()
  {
      RunFunctions();
  }
}