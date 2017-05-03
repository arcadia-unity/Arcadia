using UnityEngine;
using clojure.lang;

public class OnCollisionStayHook : ArcadiaBehaviour   
{
  public void OnCollisionStay(UnityEngine.Collision a)
  {

  	RunFunctions(a);

  }
}