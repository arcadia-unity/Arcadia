using UnityEngine;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour   
{
  public void OnMouseUp()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}