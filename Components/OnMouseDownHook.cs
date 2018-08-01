#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseDownHook : ArcadiaBehaviour
{
  public void OnMouseDown()
  {
      RunFunctions();
  }
}
#endif