using UnityEngine;
using clojure.lang;

public class OnJointBreakHook : ArcadiaBehaviour   
{
  public void OnJointBreak(System.Single a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}