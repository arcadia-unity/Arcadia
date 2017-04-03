using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour   
{
  public void OnDrawGizmosSelected()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}