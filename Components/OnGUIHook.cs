using UnityEngine;
using clojure.lang;

public class OnGUIHook : ArcadiaBehaviour   
{
  public void OnGUI()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}