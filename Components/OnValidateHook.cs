#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnValidateHook : ArcadiaBehaviour
{
  public void OnValidate()
  {
      RunFunctions();
  }
}
#endif