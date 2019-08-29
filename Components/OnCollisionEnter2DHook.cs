using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCollisionEnter2DHook : ArcadiaBehaviour
{
  public void OnCollisionEnter2D(UnityEngine.Collision2D a)
  {
      RunFunctions(a);
  }
}