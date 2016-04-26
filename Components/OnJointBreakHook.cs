using UnityEngine;
using clojure.lang;

public class OnJointBreakHook : ArcadiaBehaviour   
{
  public void OnJointBreak(System.Single a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}