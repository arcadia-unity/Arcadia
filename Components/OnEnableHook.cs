using UnityEngine;
using clojure.lang;

public class OnEnableHook : ArcadiaBehaviour
{
  void OnEnable()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}