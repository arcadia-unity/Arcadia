using UnityEngine;
using clojure.lang;

public class OnTriggerStayHook : ArcadiaBehaviour   
{
  public void OnTriggerStay(UnityEngine.Collider a)
  {

  	RunFunctions(a);

  }
}