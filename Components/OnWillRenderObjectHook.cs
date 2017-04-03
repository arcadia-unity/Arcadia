using UnityEngine;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour   
{
  public void OnWillRenderObject()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}