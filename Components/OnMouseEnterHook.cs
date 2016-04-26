using UnityEngine;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour   
{
  public void OnMouseEnter()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}