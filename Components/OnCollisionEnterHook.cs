using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCollisionEnterHook : ArcadiaBehaviour
{
  public void OnCollisionEnter(UnityEngine.Collision a)
  {
      RunFunctions(a);
  }
}