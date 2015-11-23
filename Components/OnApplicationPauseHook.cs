using UnityEngine;
using clojure.lang;

public class OnApplicationPauseHook : ArcadiaBehaviour
{
  void OnApplicationPause(System.Boolean G__18647)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18647);
  }
}