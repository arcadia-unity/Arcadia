using UnityEngine;
using clojure.lang;

public class OnLevelWasLoadedHook : ArcadiaBehaviour
{
  void OnLevelWasLoaded(System.Int32 G__18662)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18662);
  }
}