using UnityEngine;
using clojure.lang;

public class OnLevelWasLoadedHook : ArcadiaBehaviour   
{
  public void OnLevelWasLoaded(System.Int32 a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}