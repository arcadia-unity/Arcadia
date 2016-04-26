using UnityEngine;
using clojure.lang;

public class OnMouseDragHook : ArcadiaBehaviour   
{
  public void OnMouseDrag()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}