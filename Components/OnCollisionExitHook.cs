#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnCollisionExitHook : ArcadiaBehaviour
{
  public void OnCollisionExit(UnityEngine.Collision a)
  {
      RunFunctions(a);
  }
}
#endif