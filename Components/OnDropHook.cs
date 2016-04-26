using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDropHook : ArcadiaBehaviour, IDropHandler   
{
  public void OnDrop(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}