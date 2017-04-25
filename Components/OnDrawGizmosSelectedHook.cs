using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour   
{
	public void OnDrawGizmosSelected ()
	{

		RunFunctions();

	}
}