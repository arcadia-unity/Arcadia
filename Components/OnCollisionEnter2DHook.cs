using UnityEngine;
using clojure.lang;

public class OnCollisionEnter2DHook : ArcadiaBehaviour
{
  void OnCollisionEnter2D(UnityEngine.Collision2D G__18661)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18661);
  }
}