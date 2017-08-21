using UnityEngine;
using clojure.lang;

public class OnDisableHook : ArcadiaBehaviour   
{
  public void OnDisable()
  {

  	RunFunctions();

  }
}