using UnityEngine;
using clojure.lang;

public class OnMouseOverHook : ArcadiaBehaviour   
{
  public void OnMouseOver()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}