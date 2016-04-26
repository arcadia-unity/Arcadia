using UnityEngine;
using clojure.lang;

public class OnMouseOverHook : ArcadiaBehaviour   
{
  public void OnMouseOver()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}