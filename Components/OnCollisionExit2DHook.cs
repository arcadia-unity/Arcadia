using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCollisionExit2DHook : ArcadiaBehaviour
{
  public void OnCollisionExit2D(UnityEngine.Collision2D a)
  {
      RunFunctions(a);
  }
}