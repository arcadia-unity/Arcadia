using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnInitializePotentialDragHook : ArcadiaBehaviour, IInitializePotentialDragHandler   
{
  public void OnInitializePotentialDrag(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}