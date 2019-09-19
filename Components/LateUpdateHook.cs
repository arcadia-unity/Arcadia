using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class LateUpdateHook : ArcadiaBehaviour
{
  public void LateUpdate()
  {
      RunFunctions();
  }
}