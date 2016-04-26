using UnityEngine;
using clojure.lang;

public class OnTriggerEnter2DHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter2D(UnityEngine.Collider2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}