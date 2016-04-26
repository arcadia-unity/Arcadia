using UnityEngine;
using clojure.lang;

public class UpdateHook : ArcadiaBehaviour   
{
  public void Update()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}