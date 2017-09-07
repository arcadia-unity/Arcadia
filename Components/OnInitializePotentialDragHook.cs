using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnInitializePotentialDragHook : ArcadiaBehaviour, IInitializePotentialDragHandler   
{
  public void OnInitializePotentialDrag(PointerEventData a)
  {

  	RunFunctions(a);

  }
}