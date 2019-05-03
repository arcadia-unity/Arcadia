using System;
using System.IO;

namespace Arcadia
{
    public static class BasicPaths
    {
        private static string bestGuessDataPath;

        public static string BestGuessDataPath
        {
            get
            {
                if (bestGuessDataPath == null)
                {
                    bestGuessDataPath = Path.Combine(Directory.GetCurrentDirectory(), "Assets");
                }
                return bestGuessDataPath;
            }
        }

        private static string pathToCompiled;

        public static string PathToCompiled
        {
            get
            {
                if (pathToCompiled == null)
                {
                    pathToCompiled = Path.GetFullPath(Path.Combine(BestGuessDataPath, "..", "Arcadia", "Compiled"));
                }
                return pathToCompiled;
            }
        }

        private static string clojureDllFolder;

        public static string ClojureDllFolder
        {
            get
            {
                if (clojureDllFolder == null)
                {
                    clojureDllFolder = Path.GetDirectoryName(typeof(clojure.lang.RT).Assembly.Location).Substring(Directory.GetCurrentDirectory().Length + 1);
                }
                return clojureDllFolder;
            }
        }

        private static string arcadiaFolder;

        public static string ArcadiaFolder
        {
            get
            {
                if (arcadiaFolder == null)
                {
                    arcadiaFolder = Directory.GetParent(BasicPaths.ClojureDllFolder).ToString();
                }
                return arcadiaFolder;
            }
        }

        private static string pathToCompiledForExport;

        public static string PathToCompiledForExport
        {
            get
            {
                if (pathToCompiledForExport == null)
                {
                    pathToCompiledForExport = Path.GetFullPath(Path.Combine(BasicPaths.ArcadiaFolder, "Arcadia", "Export"));
                }
                return pathToCompiledForExport;
            }
        }

    }
}
