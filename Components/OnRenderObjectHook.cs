using UnityEngine;
using clojure.lang;

public class OnRenderObjectHook : ArcadiaBehaviour
{
  void OnRenderObject()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}