using UnityEngine;
using clojure.lang;

public class OnRenderImageHook : ArcadiaBehaviour   
{
  public void OnRenderImage(UnityEngine.RenderTexture a, UnityEngine.RenderTexture b)
  {
      var _go = gameObject;
      for (int i = 0; i < fns.Length; i++){
        var fn = fns[i];
        if (fn != null){
          fn.invoke(_go, a, b);
        } else {
          Debug.LogException(new System.Exception("Unresolved var: #'"+qualifiedVarNames[i]));
        }
      }
  }
}