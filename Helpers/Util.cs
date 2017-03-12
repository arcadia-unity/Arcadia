using System;
using UnityEngine;
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
			if (!Application.isPlaying){
				EditorSceneManager.MarkAllScenesDirty();
			}
			#endif
		}
	}
}