using UnityEngine;
using clojure.lang;

public class OnMouseExitHook : ArcadiaBehaviour   
{
  public void OnMouseExit()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}