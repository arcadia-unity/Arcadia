#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnMouseOverHook : ArcadiaBehaviour
{
  public void OnMouseOver()
  {
      RunFunctions();
  }
}
#endif