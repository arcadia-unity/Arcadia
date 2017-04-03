using UnityEngine;
using clojure.lang;

public class OnMouseUpAsButtonHook : ArcadiaBehaviour   
{
  public void OnMouseUpAsButton()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}