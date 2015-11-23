using UnityEngine;
using clojure.lang;

public class OnPreRenderHook : ArcadiaBehaviour
{
  void OnPreRender()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}