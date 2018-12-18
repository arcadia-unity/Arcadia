using System;
using UnityEngine;

// Facilities to query status of Unity, the build, etc.

// Exposes all preprocessor directives given at
// https://docs.unity3d.com/Manual/PlatformDependentCompilation.html
// as public static boolean properties.

// Also exposes boolean properties for whether the system is in the editor, 
// and for whether it is in Edit mode or in Play mode.

namespace Arcadia
{	
	public static class UnityStatusHelper
	{

		// ============================================================
		// Boolean properties for preprocessor directives

		// Can use these to build compile-if-style macros to get equivalent of 
		// Unity's preprocessor behavior in Clojure


		public static bool IsUnityEditor
		{
			get
			{
				#if UNITY_EDITOR
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityEditorWin
		{
			get
			{
				#if UNITY_EDITOR_WIN
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityEditorOsx
		{
			get
			{
				#if UNITY_EDITOR_OSX
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}
		
		public static bool IsUnityEditorLinux
		{
			get
			{
				#if UNITY_EDITOR_LINUX
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityStandaloneOsx
		{
			get
			{
				#if UNITY_STANDALONE_OSX
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityStandaloneWin
		{
			get
			{
				#if UNITY_STANDALONE_WIN
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityStandaloneLinux
		{
			get
			{
				#if UNITY_STANDALONE_LINUX
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityStandalone
		{
			get
			{
				#if UNITY_STANDALONE
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWii
		{
			get
			{
				#if UNITY_WII
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityIos
		{
			get
			{
				#if UNITY_IOS
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityIphone
		{
			get
			{
				#if UNITY_IPHONE
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityAndroid
		{
			get
			{
				#if UNITY_ANDROID
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityPs3
		{
			get
			{
				#if UNITY_PS3
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityPs4
		{
			get
			{
				#if UNITY_PS4
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnitySamsungtv
		{
			get
			{
				#if UNITY_SAMSUNGTV
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityXbox360
		{
			get
			{
				#if UNITY_XBOX360
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityXboxone
		{
			get
			{
				#if UNITY_XBOXONE
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityTizen
		{
			get
			{
				#if UNITY_TIZEN
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityTvos
		{
			get
			{
				#if UNITY_TVOS
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWp_8
		{
			get
			{
				#if UNITY_WP_8
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWp_8_1
		{
			get
			{
				#if UNITY_WP_8_1
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWsa
		{
			get
			{
				#if UNITY_WSA
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWsa_8_0
		{
			get
			{
				#if UNITY_WSA_8_0
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWsa_8_1
		{
			get
			{
				#if UNITY_WSA_8_1
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWsa_10_0
		{
			get
			{
				#if UNITY_WSA_10_0
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWinrt
		{
			get
			{
				#if UNITY_WINRT
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWinrt_8_0
		{
			get
			{
				#if UNITY_WINRT_8_0
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWinrt_8_1
		{
			get
			{
				#if UNITY_WINRT_8_1
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWinrt_10_0
		{
			get
			{
				#if UNITY_WINRT_10_0
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityWebgl
		{
			get
			{
				#if UNITY_WEBGL
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityAds
		{
			get
			{
				#if UNITY_ADS
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityAnalytics
		{
			get
			{
				#if UNITY_ANALYTICS
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsUnityAssertions
		{
			get
			{
				#if UNITY_ASSERTIONS
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsEnableMono
		{
			get
			{
				#if ENABLE_MONO
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsEnableIl2cpp
		{
			get
			{
				#if ENABLE_IL2CPP
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsEnableDotnet
		{
			get
			{
				#if ENABLE_DOTNET
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		public static bool IsDevelopmentBuild
		{
			get
			{
				#if DEVELOPMENT_BUILD
					return true;
				
				#else 
					return false;
				
				#endif
			}
			
		}

		// ============================================================
		// Editor vs deployed; Edit mode; Play mode.

		public static bool IsInPlayMode
		{
			get
			{
				return Application.isPlaying;
			}
		}

		public static bool IsInEditor = Application.isEditor;

		public static bool IsInEditMode
		{
			get
			{
				return IsInEditor && !IsInPlayMode;
			}
		}

	}
}

