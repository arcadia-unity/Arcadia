using UnityEngine;
using clojure.lang;

public class OnDisconnectedFromServerHook : ArcadiaBehaviour
{
  void OnDisconnectedFromServer(UnityEngine.NetworkDisconnection G__18660)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18660);
  }
}