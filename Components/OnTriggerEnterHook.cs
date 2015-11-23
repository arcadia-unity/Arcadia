using UnityEngine;
using clojure.lang;

public class OnTriggerEnterHook : ArcadiaBehaviour
{
  void OnTriggerEnter(UnityEngine.Collider G__18665)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18665);
  }
}