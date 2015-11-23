using UnityEngine;
using clojure.lang;

public class OnTriggerEnter2DHook : ArcadiaBehaviour
{
  void OnTriggerEnter2D(UnityEngine.Collider2D G__18659)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18659);
  }
}