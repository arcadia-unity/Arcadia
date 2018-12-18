#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnTriggerStayHook : ArcadiaBehaviour
{
  public void OnTriggerStay(UnityEngine.Collider a)
  {
      RunFunctions(a);
  }
}
#endif