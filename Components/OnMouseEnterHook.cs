using UnityEngine;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour   
{
  public void OnMouseEnter()
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go);
      }
  }
}