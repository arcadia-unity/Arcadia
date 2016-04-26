using UnityEngine;
using clojure.lang;

public class OnDestroyHook : ArcadiaBehaviour   
{
  public void OnDestroy()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}