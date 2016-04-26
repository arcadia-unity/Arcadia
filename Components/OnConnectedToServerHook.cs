using UnityEngine;
using clojure.lang;

public class OnConnectedToServerHook : ArcadiaBehaviour   
{
  public void OnConnectedToServer()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}