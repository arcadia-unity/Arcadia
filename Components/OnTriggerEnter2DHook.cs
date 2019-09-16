using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnTriggerEnter2DHook : ArcadiaBehaviour
{
  public void OnTriggerEnter2D(UnityEngine.Collider2D a)
  {
      RunFunctions(a);
  }
}