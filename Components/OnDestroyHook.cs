using UnityEngine;
using clojure.lang;

public class OnDestroyHook : ArcadiaBehaviour
{
  void OnDestroy()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}