using System;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	public static class EditorCallbacks
	{
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
	}
}