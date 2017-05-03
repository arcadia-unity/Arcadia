using UnityEngine;
using clojure.lang;

public class AwakeHook : ArcadiaBehaviour   
{
  public override void Awake()
  {
      base.Awake();
		RunFunctions();
  }
}