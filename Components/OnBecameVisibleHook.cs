using UnityEngine;
using clojure.lang;

public class OnBecameVisibleHook : ArcadiaBehaviour   
{
  public void OnBecameVisible()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}