using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerUpHook : ArcadiaBehaviour, IPointerUpHandler   
{
  public void OnPointerUp(PointerEventData a)
  {

  	RunFunctions(a);

  }
}