using UnityEngine;
using clojure.lang;

public class OnLevelWasLoadedHook : ArcadiaBehaviour   
{
  public void OnLevelWasLoaded(System.Int32 a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}