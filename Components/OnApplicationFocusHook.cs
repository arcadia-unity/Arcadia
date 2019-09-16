using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnApplicationFocusHook : ArcadiaBehaviour
{
  public void OnApplicationFocus(System.Boolean a)
  {
      RunFunctions(a);
  }
}