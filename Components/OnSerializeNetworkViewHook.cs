using UnityEngine;
using clojure.lang;

public class OnSerializeNetworkViewHook : ArcadiaBehaviour   
{
  public void OnSerializeNetworkView(UnityEngine.BitStream a, UnityEngine.NetworkMessageInfo b)
  {

  	RunFunctions(a, b);

  }
}