using UnityEngine;
using clojure.lang;

public class OnDisableHook : ArcadiaBehaviour
{
  void OnDisable()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}