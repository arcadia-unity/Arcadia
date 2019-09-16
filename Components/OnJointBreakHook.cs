using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnJointBreakHook : ArcadiaBehaviour
{
  public void OnJointBreak(System.Single a)
  {
      RunFunctions(a);
  }
}