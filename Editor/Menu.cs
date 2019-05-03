using UnityEditor;

namespace Arcadia
{
    public class Menu
    {
        [MenuItem("Arcadia/AOT Compile")]
        public static void AOTCompile()
        {
            Initialization.PurgeAllCompiled();
            BuildPipeline.BuildAll();
        }

        [MenuItem("Arcadia/Prepare for Export")]
        public static void PrepareForExport()
        {
            BuildPipeline.PrepareExport();
        }

        [MenuItem("Arcadia/Clean/Clean All")]
        public static void CleanAll()
        {
            Initialization.PurgeAllCompiled();
        }

        [MenuItem("Arcadia/Clean/Clean Compiled")]
        public static void CleanCompiled()
        {
            BuildPipeline.CleanCompiled();
        }
    }
}