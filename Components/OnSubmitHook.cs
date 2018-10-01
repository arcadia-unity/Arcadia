#if NET_4_6
using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSubmitHook : ArcadiaBehaviour, ISubmitHandler
{
  public void OnSubmit(BaseEventData a)
  {
      RunFunctions(a);
  }
}
#endif