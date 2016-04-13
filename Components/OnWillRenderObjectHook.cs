using UnityEngine;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour
{
  void OnWillRenderObject()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}