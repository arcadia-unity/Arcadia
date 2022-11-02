using System;
using System.IO;

namespace Arcadia.Ifrit
{
    public static class FileConsole
    {
        static string logFilePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ifrit.log");

        public static void Log(string format, params object[] arguments)
        {
            //var s = String.Format("[{0}] {1}", DateTime.Now.ToString(), string.Format(format, arguments));
            //using (StreamWriter sw = File.AppendText(logFilePath))
            //{
            //    sw.WriteLine(s);
            //}
            //UnityEngine.Debug.Log(s);
        }
    }
}