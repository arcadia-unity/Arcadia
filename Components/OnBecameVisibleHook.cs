using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnBecameVisibleHook : ArcadiaBehaviour
{
  public void OnBecameVisible()
  {
      RunFunctions();
  }
}