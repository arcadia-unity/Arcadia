using UnityEngine;
using clojure.lang;

public class OnNetworkInstantiateHook : ArcadiaBehaviour
{
  void OnNetworkInstantiate(UnityEngine.NetworkMessageInfo G__18646)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18646);
  }
}