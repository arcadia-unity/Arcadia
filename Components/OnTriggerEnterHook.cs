using UnityEngine;
using clojure.lang;

public class OnTriggerEnterHook : ArcadiaBehaviour   
{
  public void OnTriggerEnter(UnityEngine.Collider a)
  {

  	RunFunctions(a);

  }
}