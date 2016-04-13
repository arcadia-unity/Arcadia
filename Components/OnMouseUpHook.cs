using UnityEngine;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour
{
  void OnMouseUp()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}