using UnityEngine;
using clojure.lang;

public class OnNetworkInstantiateHook : ArcadiaBehaviour   
{
  public void OnNetworkInstantiate(UnityEngine.NetworkMessageInfo a)
  {

  	RunFunctions(a);

  }
}