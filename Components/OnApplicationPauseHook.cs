using UnityEngine;
using clojure.lang;

public class OnApplicationPauseHook : ArcadiaBehaviour   
{
  public void OnApplicationPause(System.Boolean a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}