using UnityEngine;
using clojure.lang;

public class OnAnimatorIKHook : ArcadiaBehaviour   
{
  public void OnAnimatorIK(System.Int32 a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}