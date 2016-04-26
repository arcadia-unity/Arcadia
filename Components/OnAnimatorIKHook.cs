using UnityEngine;
using clojure.lang;

public class OnAnimatorIKHook : ArcadiaBehaviour   
{
  public void OnAnimatorIK(System.Int32 a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}