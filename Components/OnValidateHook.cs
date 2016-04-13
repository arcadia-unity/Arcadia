using UnityEngine;
using clojure.lang;

public class OnValidateHook : ArcadiaBehaviour
{
  void OnValidate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}