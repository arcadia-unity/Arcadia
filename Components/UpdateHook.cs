using UnityEngine;
using clojure.lang;

public class UpdateHook : ArcadiaBehaviour
{
  void Update()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}