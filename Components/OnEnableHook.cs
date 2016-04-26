using UnityEngine;
using clojure.lang;

public class OnEnableHook : ArcadiaBehaviour   
{
  public void OnEnable()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}