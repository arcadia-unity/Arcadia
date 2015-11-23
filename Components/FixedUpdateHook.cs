using UnityEngine;
using clojure.lang;

public class FixedUpdateHook : ArcadiaBehaviour
{
  void FixedUpdate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}