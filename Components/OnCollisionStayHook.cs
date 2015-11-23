using UnityEngine;
using clojure.lang;

public class OnCollisionStayHook : ArcadiaBehaviour
{
  void OnCollisionStay(UnityEngine.Collision G__18645)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18645);
  }
}