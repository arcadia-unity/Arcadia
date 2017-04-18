using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour   
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a, b);
      }
  }
}