using UnityEngine;
using clojure.lang;

public class OnPreRenderHook : ArcadiaBehaviour   
{
  public void OnPreRender()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}