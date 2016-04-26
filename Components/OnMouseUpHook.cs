using UnityEngine;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour   
{
  public void OnMouseUp()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}