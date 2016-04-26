using UnityEngine;
using clojure.lang;

public class OnTriggerStay2DHook : ArcadiaBehaviour   
{
  public void OnTriggerStay2D(UnityEngine.Collider2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}