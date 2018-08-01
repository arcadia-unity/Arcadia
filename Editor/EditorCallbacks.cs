#if NET_4_6
using System;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	public class EditorCallbacks
	{
				
		public class IntervalData
		{
			// the function
			public IFn f;
			// interval in milliseconds
			public int interval;
			
			public DateTime lastTrigger;

			public object key;

			public IntervalData (IFn _f, int _interval, object _key)
			{
				f = _f;
				interval = _interval;
				key = _key;
			}
		}

		public static bool initialized = false;

		public static Var callbackRunnerVar;

		public static void Initialize () {			
			if (!initialized){
				callbackRunnerVar = RT.var("arcadia.internal.editor-callbacks", "run-callbacks");
				EditorApplication.update += RunCallbacks;
				initialized = true;
			}
		}

		public static void RunCallbacks() {
			callbackRunnerVar.invoke();
		}

		// I know this looks corny
		public static void RunIntervalCallbacks(System.Object[] callbacks, IFn onError) {
			var now = DateTime.Now;
			for (int i = 0; i < callbacks.Length; i++) {
				IntervalData d = (IntervalData)callbacks[i];
				if (d.lastTrigger == null || (now - d.lastTrigger).TotalMilliseconds >= d.interval) {
					d.lastTrigger = now;
					try {
						d.f.invoke();
					} catch (Exception e) {
						onError.invoke(e, d);
					}
				}
			}
		}
	}
}
#endif