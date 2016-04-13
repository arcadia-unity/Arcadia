using UnityEngine;
using clojure.lang;

public class OnBecameInvisibleHook : ArcadiaBehaviour
{
  void OnBecameInvisible()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}