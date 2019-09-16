using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour
{
  public void OnRenderImage(UnityEngine.RenderTexture a, UnityEngine.RenderTexture b)
  {
      RunFunctions(a, b);
  }
}