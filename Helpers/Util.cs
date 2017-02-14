using System;
#if UNITY_EDITOR
using UnityEditor;
using UnityEditor.SceneManagement;
#endif

namespace Arcadia
{
	public class Util
	{
		public static void MarkScenesDirty(){
			#if UNITY_EDITOR
			EditorSceneManager.MarkAllScenesDirty();
			#endif
		}
	}
}