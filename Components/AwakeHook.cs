using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour   
{
  public override void Awake()
  {
      base.Awake();
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go);
      }
  }
}