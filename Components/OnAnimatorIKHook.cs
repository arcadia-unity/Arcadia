using UnityEngine;
using clojure.lang;

public class OnAnimatorIKHook : ArcadiaBehaviour   
{
  public void OnAnimatorIK(System.Int32 a)
  {

  	RunFunctions(a);

  }
}