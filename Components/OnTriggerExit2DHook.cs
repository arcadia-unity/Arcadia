#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnTriggerExit2DHook : ArcadiaBehaviour
{
  public void OnTriggerExit2D(UnityEngine.Collider2D a)
  {
      RunFunctions(a);
  }
}
#endif