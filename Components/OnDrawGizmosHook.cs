using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDrawGizmosHook : ArcadiaBehaviour
{
  public void OnDrawGizmos()
  {
      RunFunctions();
  }
}