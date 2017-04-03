using UnityEngine;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour   
{
  public void OnRenderImage(UnityEngine.RenderTexture a, UnityEngine.RenderTexture b)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a, b);
  }
}