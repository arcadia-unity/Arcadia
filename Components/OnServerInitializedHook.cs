using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnServerInitializedHook : ArcadiaBehaviour
{
  public void OnServerInitialized()
  {
      RunFunctions();
  }
}