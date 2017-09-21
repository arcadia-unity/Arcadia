using System;
using System.Collections.Generic;
namespace Arcadia
{
	public class TimeSnap
	{

		//(->> Arcadia.TimeSnap/GlobalTimeSnaps
		//	(map #(.time
		//	(partition 2)
		//	(map (fn[[a b]]
		//         (.TotalMilliseconds
		//	    	(System.DateTime/op_Subtraction b a)))))
		// ==========================================================
		// static state

		public static List<TimeSnap> GlobalTimeSnaps;

		// ==========================================================
		// data

		public readonly DateTime time;
		public readonly string message;

		public TimeSnap (DateTime time_, string message_)
		{
			time = time_;
			message = message_;
		}

		// ==========================================================
		// logging equipment

		public static void snap (string message)
		{
			if (GlobalTimeSnaps == null) {
				GlobalTimeSnaps = new List<TimeSnap>();
			}
			GlobalTimeSnaps.Add(new TimeSnap(DateTime.Now, message));
		}
	}
}
