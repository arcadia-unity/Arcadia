using UnityEngine;
using clojure.lang;

public class OnCollisionEnterHook : ArcadiaBehaviour
{
  void OnCollisionEnter(UnityEngine.Collision G__18650)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18650);
  }
}