using System;
using System.Collections.Generic;
namespace Arcadia
{
	public class TimeSnap
	{

		//(->> Arcadia.TimeSnap/GlobalTimeSnaps
		//	(map #(.time %))
		//	(partition 2 1)
		//	(map (fn[[a b]]
		//         (.TotalMilliseconds
		//	    	(System.DateTime/op_Subtraction b a)))))
		// ==========================================================
		// static state

		public static List<TimeSnap> GlobalTimeSnaps;

		// ==========================================================
		// data

		public readonly DateTime time;
		public readonly object message;

		public TimeSnap (DateTime time_, object message_)
		{
			time = time_;
			message = message_;
		}

		// ==========================================================
		// logging equipment

		public static void snap (object message)
		{
			if (GlobalTimeSnaps == null) {
				GlobalTimeSnaps = new List<TimeSnap>();
			}
			GlobalTimeSnaps.Add(new TimeSnap(DateTime.Now, message));
		}
	}
}
