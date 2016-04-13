using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour
{
  void OnAudioFilterRead(System.Single[] G__18675, System.Int32 G__18676)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18675, G__18676);
  }
}