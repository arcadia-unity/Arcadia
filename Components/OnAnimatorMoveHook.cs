#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnAnimatorMoveHook : ArcadiaBehaviour
{
  public void OnAnimatorMove()
  {
      RunFunctions();
  }
}
#endif