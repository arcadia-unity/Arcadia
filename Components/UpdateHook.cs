using UnityEngine;
using clojure.lang;

public class UpdateHook : ArcadiaBehaviour   
{
  public void Update()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}