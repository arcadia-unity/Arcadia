#if NET_4_6
using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
      RunFunctions(a, b);
  }
}
#endif