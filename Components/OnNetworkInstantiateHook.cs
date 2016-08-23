using UnityEngine;
using clojure.lang;

public class OnNetworkInstantiateHook : ArcadiaBehaviour   
{
  public void OnNetworkInstantiate(UnityEngine.NetworkMessageInfo a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}