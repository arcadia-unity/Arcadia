using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class UpdateHook : ArcadiaBehaviour
{
  public void Update()
  {
      RunFunctions();
  }
}