using UnityEngine;
using clojure.lang;

public class OnPreCullHook : ArcadiaBehaviour   
{
  public void OnPreCull()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}