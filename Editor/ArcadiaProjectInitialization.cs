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

        [MenuItem("Arcadia/Initialization/Setup Player Settings")]
        public static void CheckSettings()
        {
            Debug.Log("Checking Unity Settings...");
            if (PlayerSettings.apiCompatibilityLevel != ApiCompatibilityLevel.NET_2_0)
            {
                Debug.Log("Updating API Compatibility Level to .NET 20");
                PlayerSettings.apiCompatibilityLevel = ApiCompatibilityLevel.NET_2_0;
            }

            if (!PlayerSettings.runInBackground)
            {
                Debug.Log("Updating Run In Background to true");
                PlayerSettings.runInBackground = true;
            }
        }

    }
}