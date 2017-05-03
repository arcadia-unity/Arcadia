using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDropHook : ArcadiaBehaviour, IDropHandler   
{
  public void OnDrop(PointerEventData a)
  {

  	RunFunctions(a);

  }
}