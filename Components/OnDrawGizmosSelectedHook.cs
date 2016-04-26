using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour   
{
  public void OnDrawGizmosSelected()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}