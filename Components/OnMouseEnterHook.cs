#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseEnterHook : ArcadiaBehaviour
{
  public void OnMouseEnter()
  {
      RunFunctions();
  }
}
#endif