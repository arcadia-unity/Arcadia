using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnTriggerEnterHook : ArcadiaBehaviour
{
  public void OnTriggerEnter(UnityEngine.Collider a)
  {
      RunFunctions(a);
  }
}