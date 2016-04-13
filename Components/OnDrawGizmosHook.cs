using UnityEngine;
using clojure.lang;

public class OnDrawGizmosHook : ArcadiaBehaviour
{
  void OnDrawGizmos()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}