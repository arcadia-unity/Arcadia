#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseExitHook : ArcadiaBehaviour
{
  public void OnMouseExit()
  {
      RunFunctions();
  }
}
#endif