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
		var _fns = fns;
		for (int i = 0; i < _fns.Length; i++){
			var fn = _fns[i];
			fn.invoke(_go);
		}
	}
}