using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour   
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a, b);
  }
}