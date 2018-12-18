#if NET_4_6
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

		static Repl()
		{
            Util.require("arcadia.internal.repl");
		}

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
				RT.var("arcadia.internal.repl", "eval-queue").invoke();
			}
		}

		public static void StartREPL()
		{
			replSocket = (UdpClient)RT.var("arcadia.internal.repl", "start-server").invoke(11211);
			EditorApplication.update += Repl.Update;
		}

		public static void StopREPL()
		{
			RT.var("arcadia.internal.repl", "stop-server").invoke(replSocket);
			replSocket = null;
			EditorApplication.update -= Repl.Update;
		}

		void OnGUI()
		{
			bool serverRunning = RT.booleanCast(((Atom)RT.var("arcadia.internal.repl", "server-running").deref()).deref());
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
#endif