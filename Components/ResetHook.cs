using UnityEngine;
using clojure.lang;

public class ResetHook : ArcadiaBehaviour
{
  void Reset()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}