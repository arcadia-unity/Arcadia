#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnTransformParentChangedHook : ArcadiaBehaviour
{
  public void OnTransformParentChanged()
  {
      RunFunctions();
  }
}
#endif