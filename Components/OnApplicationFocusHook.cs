#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnApplicationFocusHook : ArcadiaBehaviour
{
  public void OnApplicationFocus(System.Boolean a)
  {
      RunFunctions(a);
  }
}
#endif