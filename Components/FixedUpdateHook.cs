using UnityEngine;
using clojure.lang;

public class FixedUpdateHook : ArcadiaBehaviour   
{
  public void FixedUpdate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}