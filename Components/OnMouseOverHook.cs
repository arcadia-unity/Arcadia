using UnityEngine;
using clojure.lang;

public class OnMouseOverHook : ArcadiaBehaviour
{
  void OnMouseOver()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}