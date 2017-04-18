using UnityEngine;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour   
{
  public void OnMouseUp()
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go);
      }
  }
}