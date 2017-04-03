using UnityEngine;
using clojure.lang;

public class OnParticleCollisionHook : ArcadiaBehaviour   
{
  public void OnParticleCollision(UnityEngine.GameObject a)
  {
      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(_go, a);
  }
}