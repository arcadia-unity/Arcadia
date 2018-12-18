#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnTriggerStay2DHook : ArcadiaBehaviour
{
  public void OnTriggerStay2D(UnityEngine.Collider2D a)
  {
      RunFunctions(a);
  }
}
#endif