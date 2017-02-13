using System;
#if UNITY_EDITOR
using UnityEditor;
using UnityEditor.SceneManagement;
#endif

namespace Arcadia
{
	public class Util
	{
		#if UNITY_EDITOR
		public static bool editor = true;
		#else
		public static bool editor = false;
		#endif

		public static void MarkScenesDirty(){
			#if UNITY_EDITOR
			EditorSceneManager.MarkAllScenesDirty();
			#endif
		}
	}
}