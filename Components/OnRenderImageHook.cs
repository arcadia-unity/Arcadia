using UnityEngine;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour
{
  void OnRenderImage(UnityEngine.RenderTexture G__18655, UnityEngine.RenderTexture G__18656)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18655, G__18656);
  }
}