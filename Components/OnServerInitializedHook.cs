using UnityEngine;
using clojure.lang;

public class OnServerInitializedHook : ArcadiaBehaviour
{
  void OnServerInitialized()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}