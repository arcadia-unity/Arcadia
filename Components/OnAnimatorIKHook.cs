using UnityEngine;
using clojure.lang;

public class OnAnimatorIKHook : ArcadiaBehaviour
{
  void OnAnimatorIK(System.Int32 G__18667)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18667);
  }
}