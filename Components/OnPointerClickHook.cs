using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerClickHook : ArcadiaBehaviour, IPointerClickHandler   
{
  public void OnPointerClick(PointerEventData a)
  {

  	RunFunctions(a);

  }
}