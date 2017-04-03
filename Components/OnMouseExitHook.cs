using UnityEngine;
using clojure.lang;

public class OnMouseExitHook : ArcadiaBehaviour   
{
  public void OnMouseExit()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}