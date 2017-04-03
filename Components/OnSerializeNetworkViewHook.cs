using UnityEngine;
using clojure.lang;

public class OnSerializeNetworkViewHook : ArcadiaBehaviour   
{
  public void OnSerializeNetworkView(UnityEngine.BitStream a, UnityEngine.NetworkMessageInfo b)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a, b);
  }
}