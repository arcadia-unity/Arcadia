using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour
{
  void OnDrawGizmosSelected()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}