using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using clojure.lang;

namespace Arcadia
{

	public class PlayerCallbacks : MonoBehaviour
	{
		// ============================================================
		// data

		public static bool initialized = false;

		public static Var updateCallbackRunnerVar;

		public static Var fixedUpdateCallbackRunnerVar;

		public static Var isActiveCallbackComponentVar;

		// ============================================================
		// initialization

		public static void Initialize ()
		{
			if (!initialized) {
				string callbackNs = "arcadia.internal.player-callbacks";
				Arcadia.Util.getVar(ref updateCallbackRunnerVar, callbackNs, "run-update-callbacks");
				Arcadia.Util.getVar(ref fixedUpdateCallbackRunnerVar, callbackNs, "run-fixed-update-callbacks");
				Arcadia.Util.getVar(ref isActiveCallbackComponentVar, callbackNs, "active-callback-component?");
				initialized = true;
			}
		}

		// ============================================================
		// singleton behavior

		bool CheckIfShouldRun ()
		{
			Initialize();
			if ((bool)(isActiveCallbackComponentVar.invoke(this))) {
				return true;
			}
			Destroy(this.gameObject);
			return false;
		}

		// ============================================================
		// messages

		void Start ()
		{
			Initialize();
			CheckIfShouldRun();
		}

		void Update ()
		{
			if (CheckIfShouldRun()) {
				updateCallbackRunnerVar.invoke();
			}
		}

		void FixedUpdate ()
		{
			if (CheckIfShouldRun()) {
				fixedUpdateCallbackRunnerVar.invoke();
			}
		}
	}
}
