using UnityEngine;
using clojure.lang;

public class OnCollisionExit2DHook : ArcadiaBehaviour   
{
  public void OnCollisionExit2D(UnityEngine.Collision2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}