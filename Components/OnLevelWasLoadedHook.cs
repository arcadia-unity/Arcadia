using UnityEngine;
using clojure.lang;

public class OnLevelWasLoadedHook : ArcadiaBehaviour   
{
  public void OnLevelWasLoaded(System.Int32 a)
  {

  	RunFunctions(a);

  }
}