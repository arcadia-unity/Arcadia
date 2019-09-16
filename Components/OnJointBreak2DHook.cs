using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnJointBreak2DHook : ArcadiaBehaviour
{
  public void OnJointBreak2D(UnityEngine.Joint2D a)
  {
      RunFunctions(a);
  }
}