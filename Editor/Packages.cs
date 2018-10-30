using System;
using System.IO;
using clojure.lang;
using UnityEditor;
using UnityEngine;

namespace Arcadia
{
    public static class Packages
    {
        private static Var _restoreFromConfigVar = RT.var("arcadia.packages", "restore-from-config");
        private static Var _cleanLibraiesVar = RT.var("arcadia.packages", "clean-libraries");
        private static Var _cleanCacheVar = RT.var("arcadia.packages", "clean-cache");

        static Packages()
        {
            Util.require("arcadia.packages");
        }
        
        [MenuItem("Arcadia/Packages/Restore")]
        public static void Restore()
        {
            _restoreFromConfigVar.invoke();
        }
        
        [MenuItem("Arcadia/Packages/Clean Libraries")]
        public static void CleanLibraries()
        {
            _cleanLibraiesVar.invoke();
        }
        
        [MenuItem("Arcadia/Packages/Clean Cache")]
        public static void CleanCache()
        {
            _cleanCacheVar.invoke();
        }
    }
}