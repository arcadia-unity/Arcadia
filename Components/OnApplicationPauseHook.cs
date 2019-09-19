using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnApplicationPauseHook : ArcadiaBehaviour
{
  public void OnApplicationPause(System.Boolean a)
  {
      RunFunctions(a);
  }
}