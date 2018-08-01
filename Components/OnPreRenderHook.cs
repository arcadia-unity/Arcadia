#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnPreRenderHook : ArcadiaBehaviour
{
  public void OnPreRender()
  {
      RunFunctions();
  }
}
#endif