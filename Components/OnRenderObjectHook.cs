using UnityEngine;
using clojure.lang;

public class OnRenderObjectHook : ArcadiaBehaviour   
{
  public void OnRenderObject()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}