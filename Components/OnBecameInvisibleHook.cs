using UnityEngine;
using clojure.lang;

public class OnBecameInvisibleHook : ArcadiaBehaviour   
{
  public void OnBecameInvisible()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}