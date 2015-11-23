using UnityEngine;
using clojure.lang;

public class OnMouseUpAsButtonHook : ArcadiaBehaviour
{
  void OnMouseUpAsButton()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}