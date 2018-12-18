using System.Collections;
using System;
using System.Collections.Generic;
using UnityEngine;
using clojure.lang;

namespace Arcadia
{
	public static class Callbacks
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

		public static void RunIntervalCallbacks (System.Object[] callbacks, IFn onError)
		{
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
