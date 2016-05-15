using UnityEngine;
using clojure.lang;

public class InvokeHook : ArcadiaBehaviour
{
  public void Invoke()
  {
    if(fn != null)
      fn.invoke();
  }
}