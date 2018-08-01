#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnWillRenderObjectHook : ArcadiaBehaviour
{
  public void OnWillRenderObject()
  {
      RunFunctions();
  }
}
#endif