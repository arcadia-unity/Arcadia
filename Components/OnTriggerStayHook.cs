using UnityEngine;
using clojure.lang;

public class OnTriggerStayHook : ArcadiaBehaviour   
{
  public void OnTriggerStay(UnityEngine.Collider a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}