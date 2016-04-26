using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour   
{
  public void Awake()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}