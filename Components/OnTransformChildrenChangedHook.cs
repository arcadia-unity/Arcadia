using UnityEngine;
using clojure.lang;

public class OnTransformChildrenChangedHook : ArcadiaBehaviour   
{
  public void OnTransformChildrenChanged()
  {

    RunFunctions();

  }
}