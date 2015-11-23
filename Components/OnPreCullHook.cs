using UnityEngine;
using clojure.lang;

public class OnPreCullHook : ArcadiaBehaviour
{
  void OnPreCull()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}