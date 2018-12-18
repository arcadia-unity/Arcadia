#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnRenderObjectHook : ArcadiaBehaviour
{
  public void OnRenderObject()
  {
      RunFunctions();
  }
}
#endif