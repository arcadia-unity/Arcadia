#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnPostRenderHook : ArcadiaBehaviour
{
  public void OnPostRender()
  {
      RunFunctions();
  }
}
#endif