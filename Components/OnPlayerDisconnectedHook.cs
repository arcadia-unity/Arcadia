using UnityEngine;
using clojure.lang;

public class OnPlayerDisconnectedHook : ArcadiaBehaviour
{
  void OnPlayerDisconnected(UnityEngine.NetworkPlayer G__18671)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18671);
  }
}