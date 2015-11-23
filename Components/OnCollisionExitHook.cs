using UnityEngine;
using clojure.lang;

public class OnCollisionExitHook : ArcadiaBehaviour
{
  void OnCollisionExit(UnityEngine.Collision G__18658)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18658);
  }
}