using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnDeselectHook : ArcadiaBehaviour, IDeselectHandler   
{
  public void OnDeselect(BaseEventData a)
  {

  	RunFunctions(a);

  }
}