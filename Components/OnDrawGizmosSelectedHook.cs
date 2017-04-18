using UnityEngine;
using clojure.lang;

public class OnDrawGizmosSelectedHook : ArcadiaBehaviour   
{
	public void OnDrawGizmosSelected ()
	{
		// Not sure when this runs (especially on first load), 
		// so defensively checking for initialization
		if (!fullyInitialized) {
			FullInit();
		}

		var _go = gameObject;
		foreach (var fn in fns)
			fn.invoke(_go);
	}
}