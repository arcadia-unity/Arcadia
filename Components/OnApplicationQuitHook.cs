using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnApplicationQuitHook : ArcadiaBehaviour
{
  public void OnApplicationQuit()
  {
      RunFunctions();
  }
}