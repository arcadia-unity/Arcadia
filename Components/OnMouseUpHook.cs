#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseUpHook : ArcadiaBehaviour
{
  public void OnMouseUp()
  {
      RunFunctions();
  }
}
#endif