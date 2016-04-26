using UnityEngine;
using clojure.lang;

public class OnPostRenderHook : ArcadiaBehaviour   
{
  public void OnPostRender()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}