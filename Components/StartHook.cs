#if NET_4_6
using UnityEngine;
using clojure.lang;

public class StartHook : ArcadiaBehaviour
{
  public void Start()
  {
      RunFunctions();
  }
}
#endif