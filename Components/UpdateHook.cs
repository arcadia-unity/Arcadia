#if NET_4_6
using UnityEngine;
using clojure.lang;

public class UpdateHook : ArcadiaBehaviour
{
  public void Update()
  {
      RunFunctions();
  }
}
#endif