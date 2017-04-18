using UnityEngine;
using UnityEngine.EventSystems;
using clojure.lang;

public class OnPointerClickHook : ArcadiaBehaviour, IPointerClickHandler   
{
  public void OnPointerClick(PointerEventData a)
  {
      var _go = gameObject;
      var _fns = fns;
      for (int i = 0; i < _fns.Length; i++){
      	var fn = _fns[i];
      	fn.invoke(_go, a);
      }
  }
}