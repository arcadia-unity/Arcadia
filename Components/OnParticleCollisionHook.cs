using UnityEngine;
using clojure.lang;

public class OnParticleCollisionHook : ArcadiaBehaviour   
{
  public void OnParticleCollision(UnityEngine.GameObject a)
  {

  	RunFunctions(a);

  }
}