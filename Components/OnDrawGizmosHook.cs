using UnityEngine;
using clojure.lang;

public class OnDrawGizmosHook : ArcadiaBehaviour   
{
  public void OnDrawGizmos()
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go);
  }
}