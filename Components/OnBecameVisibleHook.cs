using UnityEngine;
using clojure.lang;

public class OnBecameVisibleHook : ArcadiaBehaviour
{
  void OnBecameVisible()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}