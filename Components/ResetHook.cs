using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class ResetHook : ArcadiaBehaviour
{
  public void Reset()
  {
      RunFunctions();
  }
}