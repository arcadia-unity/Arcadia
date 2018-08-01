#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnGUIHook : ArcadiaBehaviour
{
  public void OnGUI()
  {
      RunFunctions();
  }
}
#endif