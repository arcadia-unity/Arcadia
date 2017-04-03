using UnityEngine;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour   
{
  public void OnMouseEnter()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}