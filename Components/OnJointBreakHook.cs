using UnityEngine;
using clojure.lang;

public class OnJointBreakHook : ArcadiaBehaviour
{
  void OnJointBreak(System.Single G__18669)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18669);
  }
}