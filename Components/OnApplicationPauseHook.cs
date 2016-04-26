using UnityEngine;
using clojure.lang;

public class OnApplicationPauseHook : ArcadiaBehaviour   
{
  public void OnApplicationPause(System.Boolean a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}