using clojure.lang;
using UnityEditor;

namespace Arcadia
{
    public class ProgressBar
    {
        public static Atom State = new Atom(PersistentHashMap.EMPTY);
        private static Keyword _titleKw = Keyword.intern("title");
        private static Keyword _infoKw = Keyword.intern("info");
        private static Keyword _progressKw = Keyword.intern("progress");
        private static bool _stop = true; // bool assignment is atomic on the clr

        public static void Start(object state)
        {
            State.reset(state);
            _stop = false;
            EditorApplication.update += Update;
        }
        
        public static void Stop()
        {
            _stop = true;
        }

        private static void Update()
        {
            var state = (IPersistentMap) State.deref();
            var cancel = EditorUtility.DisplayCancelableProgressBar(state.valAt(_titleKw) as string, state.valAt(_infoKw) as string,
                RT.floatCast(state.valAt(_progressKw)));
            
            if (_stop || cancel)
            {
                EditorUtility.ClearProgressBar();
                // ReSharper disable once DelegateSubtraction
                EditorApplication.update -= Update;
            }
        }
    }
}