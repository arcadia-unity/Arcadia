using UnityEngine;
using clojure.lang;

public class OnCollisionStay2DHook : ArcadiaBehaviour   
{
  public void OnCollisionStay2D(UnityEngine.Collision2D a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}