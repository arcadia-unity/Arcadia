using UnityEngine;
using clojure.lang;

public class OnApplicationFocusHook : ArcadiaBehaviour
{
  void OnApplicationFocus(System.Boolean G__18674)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18674);
  }
}