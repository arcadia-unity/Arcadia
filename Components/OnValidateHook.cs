using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnValidateHook : ArcadiaBehaviour
{
  public void OnValidate()
  {
      RunFunctions();
  }
}