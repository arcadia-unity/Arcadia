using UnityEngine;
using clojure.lang;

public class OnTriggerExit2DHook : ArcadiaBehaviour
{
  void OnTriggerExit2D(UnityEngine.Collider2D G__18654)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18654);
  }
}