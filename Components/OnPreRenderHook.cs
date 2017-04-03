using UnityEngine;
using clojure.lang;

public class OnPreRenderHook : ArcadiaBehaviour   
{
  public void OnPreRender()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}