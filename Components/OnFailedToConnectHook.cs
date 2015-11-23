using UnityEngine;
using clojure.lang;

public class OnFailedToConnectHook : ArcadiaBehaviour
{
  void OnFailedToConnect(UnityEngine.NetworkConnectionError G__18673)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18673);
  }
}