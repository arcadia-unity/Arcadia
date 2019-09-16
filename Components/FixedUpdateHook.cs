using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class FixedUpdateHook : ArcadiaBehaviour
{
  public void FixedUpdate()
  {
      RunFunctions();
  }
}