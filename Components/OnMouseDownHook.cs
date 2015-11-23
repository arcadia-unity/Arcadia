using UnityEngine;
using clojure.lang;

public class OnMouseDownHook : ArcadiaBehaviour
{
  void OnMouseDown()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}