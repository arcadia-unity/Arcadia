using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBecameInvisibleHook : ArcadiaBehaviour
{
  public void OnBecameInvisible()
  {
      RunFunctions();
  }
}