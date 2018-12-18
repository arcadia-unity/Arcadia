#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseUpAsButtonHook : ArcadiaBehaviour
{
  public void OnMouseUpAsButton()
  {
      RunFunctions();
  }
}
#endif