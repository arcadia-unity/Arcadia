using UnityEngine;
using clojure.lang;

public class OnCollisionEnter2DHook : ArcadiaBehaviour   
{
  public void OnCollisionEnter2D(UnityEngine.Collision2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}