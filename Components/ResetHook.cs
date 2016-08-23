using UnityEngine;
using clojure.lang;

public class ResetHook : ArcadiaBehaviour   
{
  public void Reset()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}