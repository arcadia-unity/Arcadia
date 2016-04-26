using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour   
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
    if(fn != null)
      fn.invoke(gameObject, a, b);
  }
}