using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnSubmitHook : ArcadiaBehaviour, ISubmitHandler   
{
  public void OnSubmit(BaseEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}