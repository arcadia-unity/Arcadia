#if NET_4_6
using System;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	public class EditorCallbacks
	{
				
		public static bool initialized = false;

		public static Var callbackRunnerVar;

		public static void Initialize () {			
			if (!initialized){
				Arcadia.Util.getVar(ref callbackRunnerVar, "arcadia.internal.editor-callbacks", "run-callbacks");
				EditorApplication.update += RunCallbacks;
				initialized = true;
			}
		}

		public static void RunCallbacks() {
			callbackRunnerVar.invoke();
		}

	}
}
#endif