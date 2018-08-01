#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseDragHook : ArcadiaBehaviour
{
  public void OnMouseDrag()
  {
      RunFunctions();
  }
}
#endif