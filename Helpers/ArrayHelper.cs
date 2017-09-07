using System;
using clojure.lang;

namespace Arcadia
{
	public static class ArrayHelper
	{

		public static T[] CountedArray<T>(Counted c){
			T[] ar = new T[c.count()];
			int i = 0;
			foreach (T x in (System.Collections.IEnumerable) c) {
				ar[i] = x;
				i++;
			}
			return ar;			
		}
			
	}
}

