using UnityEngine;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour   
{
  public void OnWillRenderObject()
  {

  	RunFunctions();

  }
}