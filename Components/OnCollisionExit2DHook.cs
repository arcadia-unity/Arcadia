using UnityEngine;
using clojure.lang;

public class OnCollisionExit2DHook : ArcadiaBehaviour
{
  void OnCollisionExit2D(UnityEngine.Collision2D G__18649)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18649);
  }
}