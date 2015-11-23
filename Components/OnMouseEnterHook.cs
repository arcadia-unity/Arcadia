using UnityEngine;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour
{
  void OnMouseEnter()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}