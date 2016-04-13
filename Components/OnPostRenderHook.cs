using UnityEngine;
using clojure.lang;

public class OnPostRenderHook : ArcadiaBehaviour
{
  void OnPostRender()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}