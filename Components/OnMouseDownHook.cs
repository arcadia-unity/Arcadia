using UnityEngine;
using clojure.lang;

public class OnMouseDownHook : ArcadiaBehaviour   
{
  public void OnMouseDown()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}