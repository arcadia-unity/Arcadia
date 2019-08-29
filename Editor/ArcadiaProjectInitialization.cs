using UnityEngine;
using UnityEditor;

namespace Arcadia
{
    [InitializeOnLoad]
    public class ArcadiaProjectInitialization
        // Starts with an "A" because Editor scripts are initialized alphabetically 
    {
        static ArcadiaProjectInitialization()
        {
            CheckSettings();
        }

        public static void CheckSettings()
        {
			Debug.Log("Checking Unity Settings...");
            if (!Application.unityVersion.StartsWith("2018")) // gross string comparison, not sure we can avoid
            {
                Debug.LogWarningFormat("Expected Unity version 2018.x, got {0}. This might cause issues.", Application.unityVersion);
            }

            if (!PlayerSettings.runInBackground)
            {
                Debug.Log("Updating Run In Background to true");
                PlayerSettings.runInBackground = true;
            }
        }

    }
}