using UnityEngine;
using clojure.lang;

public class OnTriggerStayHook : ArcadiaBehaviour
{
  void OnTriggerStay(UnityEngine.Collider G__18668)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18668);
  }
}