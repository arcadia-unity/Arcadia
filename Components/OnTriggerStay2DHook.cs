using UnityEngine;
using clojure.lang;

public class OnTriggerStay2DHook : ArcadiaBehaviour
{
  void OnTriggerStay2D(UnityEngine.Collider2D G__18664)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18664);
  }
}