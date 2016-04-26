using UnityEngine;
using clojure.lang;

public class OnDisableHook : ArcadiaBehaviour   
{
  public void OnDisable()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}