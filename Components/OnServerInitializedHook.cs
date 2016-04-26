using UnityEngine;
using clojure.lang;

public class OnServerInitializedHook : ArcadiaBehaviour   
{
  public void OnServerInitialized()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}