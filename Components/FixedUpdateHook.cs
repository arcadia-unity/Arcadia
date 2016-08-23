using UnityEngine;
using clojure.lang;

public class FixedUpdateHook : ArcadiaBehaviour   
{
  public void FixedUpdate()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}