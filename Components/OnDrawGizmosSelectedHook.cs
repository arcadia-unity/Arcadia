using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour
{
  public void OnDrawGizmosSelected()
  {
      RunFunctions();
  }
}