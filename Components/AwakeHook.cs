using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour   
{
  public override void Awake()
  {
      base.Awake();
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}