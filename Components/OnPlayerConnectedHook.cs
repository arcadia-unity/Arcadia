using UnityEngine;
using clojure.lang;

public class OnPlayerConnectedHook : ArcadiaBehaviour
{
  void OnPlayerConnected(UnityEngine.NetworkPlayer G__18666)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18666);
  }
}