using UnityEngine;
using clojure.lang;

public class OnConnectedToServerHook : ArcadiaBehaviour   
{
  public void OnConnectedToServer()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}