using UnityEngine;
using clojure.lang;

public class OnCollisionExitHook : ArcadiaBehaviour   
{
  public void OnCollisionExit(UnityEngine.Collision a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}