using UnityEngine;
using clojure.lang;

public class OnCollisionStayHook : ArcadiaBehaviour   
{
  public void OnCollisionStay(UnityEngine.Collision a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}