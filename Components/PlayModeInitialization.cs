using System;
using System.Reflection;


// Purpose of this class is to ensure loadpaths are initialized during
// play mode in the editor. Unfortunately this currently requires running
// Initialization.Initialize, which is an Editor script, and therefore
// apparently unavailable to user defined Components in the normal way.
// We therefore use Reflection to access it. If the script is not running
// in the editor, this is a no-op.

namespace Arcadia
{
    public static class PlayModeInitialization
    {
        public static bool initialized;

        public static void Initialize1()
        {
            if (initialized)
                return;

#if UNITY_EDITOR

            System.Type editorInitializationType = Type.GetType("Arcadia.Initialization, Assembly-CSharp-Editor");
            MethodInfo initializeMethod = editorInitializationType.GetMethod("Initialize");

            initializeMethod.Invoke(null, new object[0]);

#endif
            initialized = true;
        }
    }
}
