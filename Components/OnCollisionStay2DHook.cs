using UnityEngine;
using clojure.lang;

public class OnCollisionStay2DHook : ArcadiaBehaviour
{
  void OnCollisionStay2D(UnityEngine.Collision2D G__18663)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18663);
  }
}