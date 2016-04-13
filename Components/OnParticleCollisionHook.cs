using UnityEngine;
using clojure.lang;

public class OnParticleCollisionHook : ArcadiaBehaviour
{
  void OnParticleCollision(UnityEngine.GameObject G__18672)
  {
    if(fn != null)
      fn.invoke(gameObject, G__18672);
  }
}