using UnityEngine;
using clojure.lang;

public class OnParticleCollisionHook : ArcadiaBehaviour   
{
  public void OnParticleCollision(UnityEngine.GameObject a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}