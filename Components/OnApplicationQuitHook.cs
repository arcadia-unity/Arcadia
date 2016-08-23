using UnityEngine;
using clojure.lang;

public class OnApplicationQuitHook : ArcadiaBehaviour   
{
  public void OnApplicationQuit()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}