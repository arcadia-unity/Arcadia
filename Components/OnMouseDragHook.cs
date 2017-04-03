using UnityEngine;
using clojure.lang;

public class OnMouseDragHook : ArcadiaBehaviour   
{
  public void OnMouseDrag()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}