using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnConnectedToServerHook : ArcadiaBehaviour
{
  public void OnConnectedToServer()
  {
      RunFunctions();
  }
}