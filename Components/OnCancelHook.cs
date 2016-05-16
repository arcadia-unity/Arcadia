using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnCancelHook : ArcadiaBehaviour, ICancelHandler   
{
  public void OnCancel(BaseEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}