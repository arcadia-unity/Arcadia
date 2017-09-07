using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerEnterHook : ArcadiaBehaviour, IPointerEnterHandler   
{
  public void OnPointerEnter(PointerEventData a)
  {

  	RunFunctions(a);

  }
}