#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnJointBreak2DHook : ArcadiaBehaviour
{
  public void OnJointBreak2D(UnityEngine.Joint2D a)
  {
      RunFunctions(a);
  }
}
#endif