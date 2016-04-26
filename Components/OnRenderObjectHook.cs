using UnityEngine;
using clojure.lang;

public class OnRenderObjectHook : ArcadiaBehaviour   
{
  public void OnRenderObject()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}