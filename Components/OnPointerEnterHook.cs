using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerEnterHook : ArcadiaBehaviour, IPointerEnterHandler   
{
  public void OnPointerEnter(PointerEventData a)
  {
    if(fn != null)
      fn.invoke(gameObject, a);
  }
}