using UnityEngine;
using clojure.lang;

public class OnValidateHook : ArcadiaBehaviour   
{
  public void OnValidate()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}