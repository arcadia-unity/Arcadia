#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnDrawGizmosHook : ArcadiaBehaviour
{
  public void OnDrawGizmos()
  {
      RunFunctions();
  }
}
#endif