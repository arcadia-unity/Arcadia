using UnityEngine;
using clojure.lang;

public class OnCollisionStay2DHook : ArcadiaBehaviour   
{
  public void OnCollisionStay2D(UnityEngine.Collision2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}