using UnityEngine;
using clojure.lang;

public class OnValidateHook : ArcadiaBehaviour   
{
  public void OnValidate()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}