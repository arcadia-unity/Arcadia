#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnCollisionEnter2DHook : ArcadiaBehaviour
{
  public void OnCollisionEnter2D(UnityEngine.Collision2D a)
  {
      RunFunctions(a);
  }
}
#endif