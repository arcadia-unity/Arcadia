using System.Diagnostics;
using System.IO;
using clojure.lang;
using UnityEditor;

namespace Arcadia
{
    /// <summary>
    /// Facilities to run shell programs
    /// </summary>
    public static class Shell
    {
        public static readonly string MonoExecutablePath =
#if UNITY_EDITOR_OSX
            Path.Combine(
                EditorApplication.applicationPath,
                "Contents/MonoBleedingEdge/bin/mono");
#elif UNITY_EDITOR_WIN
            Path.Combine(
                Path.GetDirectoryName(EditorApplication.applicationPath),
                "Data/MonoBleedingEdge/bin/mono.exe");
#elif UNITY_EDITOR_LINUX
            Path.Combine(
                Path.GetDirectoryName(EditorApplication.applicationPath),
                "Data/MonoBleedingEdge/bin/mono");
#endif


        public static readonly string MozrootsExePath =
#if UNITY_EDITOR_OSX
            Path.Combine(
                EditorApplication.applicationPath,
                "Contents/MonoBleedingEdge/lib/mono/4.5/mozroots.exe");
#elif UNITY_EDITOR_WIN
            Path.Combine(
                Path.GetDirectoryName(EditorApplication.applicationPath),
                "Data/MonoBleedingEdge/lib/mono/4.5/mozroots.exe");
#elif UNITY_EDITOR_LINUX
            Path.Combine(
                Path.GetDirectoryName(EditorApplication.applicationPath),
                "Data/MonoBleedingEdge/lib/mono/4.5/mozroots.exe");
#endif

        private static readonly Keyword WorkingDirectoryKeyword = Keyword.intern("directory");
        private static readonly Keyword OutputKeyword = Keyword.intern("output");
        private static readonly Keyword ErrorKeyword = Keyword.intern("error");
        private static readonly Keyword DoneKeyword = Keyword.intern("done");

        public static Process Run(string filename, string arguments = null, string workingDirectory = null, IFn outputFn = null, IFn errorFn = null, IFn doneFn = null)
        {
            Process process = new Process();
            process.StartInfo.FileName = filename;
            if (arguments != null) process.StartInfo.Arguments = arguments;
            if (workingDirectory != null) process.StartInfo.WorkingDirectory = workingDirectory;
            process.StartInfo.RedirectStandardOutput = true;
            process.StartInfo.RedirectStandardError = true;
            process.StartInfo.UseShellExecute = false;
            process.EnableRaisingEvents = true;
            process.Start();

            if (outputFn != null)
            {
                process.OutputDataReceived += (sender, args) => { if(args.Data != null) outputFn.invoke(args.Data); };
                process.BeginOutputReadLine();
            }
            if (errorFn != null)
            {
                process.ErrorDataReceived += (sender, args) => { if(args.Data != null) errorFn.invoke(args.Data); };
                process.BeginErrorReadLine();
            }
            if(doneFn != null)
                process.Exited += (sender, args) => { doneFn.invoke(); };

            return process;
        }

        public static Process MonoRun(string pathToExe, string arguments, IPersistentMap options)
        {
            var workingDirectory = (string)options.valAt(WorkingDirectoryKeyword);
            var outputFn = (IFn)options.valAt(OutputKeyword);
            var errorFn = (IFn)options.valAt(ErrorKeyword);
            var doneFn = (IFn)options.valAt(DoneKeyword);
            return Run(MonoExecutablePath, string.Join(" ", pathToExe, arguments), workingDirectory, outputFn, errorFn, doneFn);
        }

        public static Process MonoRun(string pathToExe, string arguments)
        {
            return Run(MonoExecutablePath, string.Join(" ", pathToExe, arguments));
        }

        public static Process MonoRun(string pathToExe)
        {
            return Run(MonoExecutablePath, pathToExe);
        }
    }
}
