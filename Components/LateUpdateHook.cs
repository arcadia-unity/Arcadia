using UnityEngine;
using clojure.lang;

public class LateUpdateHook : ArcadiaBehaviour
{
  void LateUpdate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}