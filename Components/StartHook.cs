using UnityEngine;
using clojure.lang;

public class StartHook : ArcadiaBehaviour
{
  void Start()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}