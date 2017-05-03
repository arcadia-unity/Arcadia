using UnityEngine;
using clojure.lang;

public class OnApplicationQuitHook : ArcadiaBehaviour   
{
  public void OnApplicationQuit()
  {

  	RunFunctions();

  }
}