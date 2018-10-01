#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour
{
  public void OnDrawGizmosSelected()
  {
      RunFunctions();
  }
}
#endif