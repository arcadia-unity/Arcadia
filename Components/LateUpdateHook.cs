using UnityEngine;
using clojure.lang;

public class LateUpdateHook : ArcadiaBehaviour   
{
  public void LateUpdate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}