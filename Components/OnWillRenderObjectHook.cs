using UnityEngine;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour   
{
  public void OnWillRenderObject()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}