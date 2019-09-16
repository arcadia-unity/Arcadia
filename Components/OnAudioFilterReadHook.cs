using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
      RunFunctions(a, b);
  }
}