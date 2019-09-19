using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCollisionExitHook : ArcadiaBehaviour
{
  public void OnCollisionExit(UnityEngine.Collision a)
  {
      RunFunctions(a);
  }
}