using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnGUIHook : ArcadiaBehaviour
{
  public void OnGUI()
  {
      RunFunctions();
  }
}