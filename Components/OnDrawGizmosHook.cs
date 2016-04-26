using UnityEngine;
using clojure.lang;

public class OnDrawGizmosHook : ArcadiaBehaviour   
{
  public void OnDrawGizmos()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}