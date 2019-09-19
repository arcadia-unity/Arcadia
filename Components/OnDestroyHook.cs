using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDestroyHook : ArcadiaBehaviour
{
  public void OnDestroy()
  {
      RunFunctions();
  }
}