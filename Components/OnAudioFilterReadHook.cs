using UnityEngine;
using clojure.lang;

public class OnAudioFilterReadHook : ArcadiaBehaviour   
{
  public void OnAudioFilterRead(System.Single[] a, System.Int32 b)
  {
      var _go = gameObject;
      for (int i = 0; i < fns.Length; i++){
        var fn = fns[i];
        if (fn != null){
          fn.invoke(_go, a, b);
        } else {
          Debug.LogException(new System.Exception("Unresolved var: #'"+qualifiedVarNames[i]));
        }
      }
  }
}