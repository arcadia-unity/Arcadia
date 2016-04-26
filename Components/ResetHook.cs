using UnityEngine;
using clojure.lang;

public class ResetHook : ArcadiaBehaviour   
{
  public void Reset()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}