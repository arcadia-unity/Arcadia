using UnityEngine;
using clojure.lang;

public class OnTriggerExitHook : ArcadiaBehaviour
{
  void OnTriggerExit(UnityEngine.Collider G__18648)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18648);
  }
}