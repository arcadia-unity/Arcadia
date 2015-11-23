using UnityEngine;
using clojure.lang;

public class OnMouseDragHook : ArcadiaBehaviour
{
  void OnMouseDrag()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}