using UnityEngine;
using clojure.lang;

public class StartHook : ArcadiaBehaviour   
{
  public void Start()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}