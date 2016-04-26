using UnityEngine;
using clojure.lang;

public class OnTriggerExit2DHook : ArcadiaBehaviour   
{
  public void OnTriggerExit2D(UnityEngine.Collider2D a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}