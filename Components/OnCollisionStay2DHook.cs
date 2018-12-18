#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnCollisionStay2DHook : ArcadiaBehaviour
{
  public void OnCollisionStay2D(UnityEngine.Collision2D a)
  {
      RunFunctions(a);
  }
}
#endif