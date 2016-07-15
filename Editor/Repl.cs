using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
using System.Net;
using System.Text;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Threading;
using System.Runtime.InteropServices;

namespace Arcadia
{
	public class Repl : EditorWindow
	{
		private static UdpClient replSocket;


#if UNITY_EDITOR_OSX
		[DllImport("ForceEditorUpdates")]
		private static extern void StartForcingEditorApplicationUpdates();
#endif

		static Repl()
		{
			RT.load("arcadia/repl");
			// kill repl when exiting unity
			AppDomain.CurrentDomain.ProcessExit += (object sender, EventArgs e) => { StopREPL(); };
		}

		[MenuItem("Arcadia/REPL/Window...")]
		public static void Init()
		{
			Repl window = (Repl)EditorWindow.GetWindow(typeof(Repl));
		}

		public static void Update()
		{
			if (EditorApplication.isCompiling || (!EditorApplication.isPlaying && EditorApplication.isPlayingOrWillChangePlaymode))
			{
				// kill the repl when entering play mode
				StopREPL();
			}
			else {
				RT.var("arcadia.repl", "eval-queue").invoke();
			}
		}

		[MenuItem("Arcadia/REPL/Start %#r")]
		public static void StartREPL()
		{
			replSocket = (UdpClient)RT.var("arcadia.repl", "start-server").invoke(11211);
			EditorApplication.update += Repl.Update;
#if UNITY_EDITOR_OSX
			StartForcingEditorApplicationUpdates();
#endif
		}

		[MenuItem("Arcadia/REPL/Stop &#r")]
		public static void StopREPL()
		{
			RT.var("arcadia.repl", "stop-server").invoke(replSocket);
			replSocket = null;
			EditorApplication.update -= Repl.Update;
		}

		void OnFocus()
		{
			// Debug.Log("StopForcingEditorApplicationUpdates");
			// StopForcingEditorApplicationUpdates();
		}

		void OnLostFocus()
		{
			// Debug.Log("StartForcingEditorApplicationUpdates");
			// StartForcingEditorApplicationUpdates();
		}

		void OnGUI()
		{
			bool serverRunning = RT.booleanCast(((Atom)RT.var("arcadia.repl", "server-running").deref()).deref());
			Color oldColor = GUI.color;
			if (serverRunning)
			{
				GUI.color = Color.red;
				if (GUILayout.Button("Stop REPL"))
				{
					Repl.StopREPL();
				}
				GUI.color = oldColor;

				if (replSocket != null)
					GUILayout.Label("REPL is listening on " + replSocket.Client.LocalEndPoint);

			}
			else {
				GUI.color = Color.green;
				if (GUILayout.Button("Start REPL"))
				{
					Repl.StartREPL();
				}
				GUI.color = oldColor;

				GUILayout.Label("REPL is not running");
			}
		}
	}
}