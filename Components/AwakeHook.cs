using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour
{
  void Awake()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}