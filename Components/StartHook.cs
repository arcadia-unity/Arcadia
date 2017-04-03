using UnityEngine;
using clojure.lang;

public class StartHook : ArcadiaBehaviour   
{
  public void Start()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}