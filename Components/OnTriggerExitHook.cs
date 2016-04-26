using UnityEngine;
using clojure.lang;

public class OnTriggerExitHook : ArcadiaBehaviour   
{
  public void OnTriggerExit(UnityEngine.Collider a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}