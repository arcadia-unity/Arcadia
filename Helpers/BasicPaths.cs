using System;
using System.IO;

namespace Arcadia
{
    public static class BasicPaths
    {
        public static string PathCombine(params string[] paths)
        {
            if(paths.Length == 0)
                return "";
            
            var combinedPath = paths[0];
            for (int i = 1; i < paths.Length; i++)
            {
                combinedPath = Path.Combine(combinedPath, paths[i]);
            }

            return combinedPath;
        }

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
                    pathToCompiled = Path.GetFullPath(PathCombine(BestGuessDataPath, "..", "Arcadia", "Compiled"));
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
                    if(typeof(clojure.lang.RT).Assembly.Location.Contains("Clojure.dll"))
                    {
                        clojureDllFolder = Path.GetDirectoryName(typeof(clojure.lang.RT).Assembly.Location).Substring(Directory.GetCurrentDirectory().Length + 1);
                    }
                    else
                    {
                        clojureDllFolder = PathCombine(BestGuessDataPath, "Arcadia", "Infrastructure");
                    }
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
                    pathToCompiledForExport = Path.GetFullPath(PathCombine(BasicPaths.ArcadiaFolder, "Arcadia", "Export"));
                }
                return pathToCompiledForExport;
            }
        }

    }
}
