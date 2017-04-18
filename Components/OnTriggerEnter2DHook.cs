using UnityEngine;
using clojure.lang;

public class OnTriggerEnter2DHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter2D(UnityEngine.Collider2D a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}