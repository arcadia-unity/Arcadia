using UnityEngine;
using clojure.lang;

public class OnSerializeNetworkViewHook : ArcadiaBehaviour
{
  void OnSerializeNetworkView(UnityEngine.BitStream G__18652, UnityEngine.NetworkMessageInfo G__18653)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18652, G__18653);
  }
}