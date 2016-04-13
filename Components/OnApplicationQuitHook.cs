using UnityEngine;
using clojure.lang;

public class OnApplicationQuitHook : ArcadiaBehaviour
{
  void OnApplicationQuit()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}