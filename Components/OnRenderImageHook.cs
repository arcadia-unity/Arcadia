using UnityEngine;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour   
{
  public void OnRenderImage(UnityEngine.RenderTexture a, UnityEngine.RenderTexture b)
  {

  	RunFunctions(a, b);

  }
}