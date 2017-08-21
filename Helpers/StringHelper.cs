using System;

namespace Arcadia
{
	public static class StringHelper
	{
		public static bool StartsWith(string s, string pat){
			if (s.Length < pat.Length){
				return false;
			}
			for (int i = 0; i < pat.Length; i++) {
				if (s[i] != pat[i]) {
					return false;
				}
			}
			return true;
		}

		public static bool StartsWithAny(string s, string[] pats){
			foreach (string pat in pats){
				if (StartsWith(s, pat)) {
					return true;
				}
			}
			return false;
		}

		// considerably faster than the EndsWith method of Strings
		public static bool EndsWith(string s, string pat){
			if (s.Length < pat.Length) {
				return false;
			}
			int offset = s.Length - pat.Length;
			for (int i = 0; i < pat.Length; i++) {
				if(s[i + offset] != pat[i]){
					return false;
				}
			}
			return true;
		}

		public static bool EndsWithAny(string s, string[] pats){
			foreach (string pat in pats){
				if (EndsWith(s, pat)) {
					return true;
				}
			}
			return false;
		}
			
	}
}

