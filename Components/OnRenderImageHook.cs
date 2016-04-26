using UnityEngine;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour   
{
  public void OnRenderImage(UnityEngine.RenderTexture a, UnityEngine.RenderTexture b)
  {
    if(fn != null)
      fn.invoke(gameObject, a, b);
  }
}