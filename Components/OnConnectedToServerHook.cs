using UnityEngine;
using clojure.lang;

public class OnConnectedToServerHook : ArcadiaBehaviour
{
  void OnConnectedToServer()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}