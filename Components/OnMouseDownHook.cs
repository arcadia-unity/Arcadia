using UnityEngine;
using clojure.lang;

public class OnMouseDownHook : ArcadiaBehaviour   
{
  public void OnMouseDown()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}