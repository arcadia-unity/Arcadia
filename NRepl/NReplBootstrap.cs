using System;
using clojure.lang;
using UnityEngine;

namespace Arcadia
{
    public class NReplBootstrap : MonoBehaviour
    {
        public int port = 3722;
        private void Awake()
        {
            Util.require("arcadia.internal.player-callbacks");
            var addCallbackIFn = RT.var("arcadia.internal.player-callbacks", "add-update-callback-without-initialization");
            NRepl.StartServer(addCallbackIFn, port);
            RT.var("arcadia.internal.player-callbacks", "callback-component").invoke();
        }
    }
}