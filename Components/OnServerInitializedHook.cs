using UnityEngine;
using clojure.lang;

public class OnServerInitializedHook : ArcadiaBehaviour   
{
  public void OnServerInitialized()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}