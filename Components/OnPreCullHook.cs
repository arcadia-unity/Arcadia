using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPreCullHook : ArcadiaBehaviour
{
  public void OnPreCull()
  {
      RunFunctions();
  }
}