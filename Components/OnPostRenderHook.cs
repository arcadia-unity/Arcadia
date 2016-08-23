using UnityEngine;
using clojure.lang;

public class OnPostRenderHook : ArcadiaBehaviour   
{
  public void OnPostRender()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}