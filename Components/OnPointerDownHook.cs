using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerDownHook : ArcadiaBehaviour, IPointerDownHandler   
{
  public void OnPointerDown(PointerEventData a)
  {

  	RunFunctions(a);

  }
}