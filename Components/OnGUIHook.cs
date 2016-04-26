using UnityEngine;
using clojure.lang;

public class OnGUIHook : ArcadiaBehaviour   
{
  public void OnGUI()
  {
    if(fn != null)
      fn.invoke(gameObject);
  }
}