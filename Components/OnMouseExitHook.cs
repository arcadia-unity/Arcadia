using UnityEngine;
using clojure.lang;

public class OnMouseExitHook : ArcadiaBehaviour
{
  void OnMouseExit()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}