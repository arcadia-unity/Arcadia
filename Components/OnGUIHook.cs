using UnityEngine;
using clojure.lang;

public class OnGUIHook : ArcadiaBehaviour
{
  void OnGUI()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}