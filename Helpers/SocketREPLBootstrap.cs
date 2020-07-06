using clojure.lang;
using System;
using System.Collections;
using System.Net;
using UnityEngine;

namespace Arcadia
{
    public class SocketREPLBootstrap : MonoBehaviour
    {
        public int port = 37221;
        private const string HostObjectName = "##<Arcadia-REPL-Host-Object>##";
        static Keyword argsKeyword;
        static Keyword callbackDriverKeyword;
        static bool CreatedHostObject = false;
        static bool didInit = false;
        static Keyword portKeyword;
        static Var startServerVar;
        static readonly Queue WorkQueue = Queue.Synchronized(new Queue());

        public static void StartRepl()
        {
            if (!CreatedHostObject)
            {
                CreatedHostObject = true;
                var go = new GameObject(HostObjectName);
                go.AddComponent<SocketREPLBootstrap>();
                DontDestroyOnLoad(go);
                System.Console.WriteLine("[socket-repl] creating {0}", go);
            }
        }

        public void SetupIfrit()
        {
            var setupNsName = "arcadiatech.intervention-api.setup";
            Arcadia.Util.require(setupNsName);
            RT.var(setupNsName, "setup").invoke(this.gameObject);
        }

        static void DoInit()
        {
            if (didInit == false)
            {
                didInit = true;
                callbackDriverKeyword = Keyword.intern("callback-driver");
                portKeyword = Keyword.intern("port");
                argsKeyword = Keyword.intern("args");
                Arcadia.Util.require("arcadia.internal.socket-repl");
                startServerVar = RT.var("arcadia.internal.socket-repl", "set-options-and-start-server");
            }
        }

        private void Awake()
        {
            DoInit();
            var addCallbackIFn = new AddCallbackFn(WorkQueue);
            System.Console.WriteLine("[socket-repl] bootstrap awake, callback fn: {0} port: {1}, addr: {2}", addCallbackIFn, port, IPAddress.Any);
            var optionsMap = RT.mapUniqueKeys(
                portKeyword, port,
                argsKeyword, RT.vector(addCallbackIFn)
            );
            startServerVar.invoke(optionsMap);
            SetupIfrit();
        }

        private void Update()
        {
            // System.Console.WriteLine("[socket-repl] bootstrap update");
            if (WorkQueue.Count > 0)
            {
                System.Console.WriteLine("[socket-repl] dequeueing {0} items", WorkQueue.Count);
                while (WorkQueue.Count > 0)
                {
                    var workItem = WorkQueue.Dequeue();
                    var cb = workItem as IFn;
                    if (cb != null)
                    {
                        System.Console.WriteLine("[socket-repl] invoking {0}", cb);
                        cb.invoke();
                    }
                    else
                    {
                        System.Console.WriteLine("[socket-repl] not invoking {0}", workItem);
                    }
                }
            }
        }

        class AddCallbackFn : AFn
        {
            private readonly Queue workQueue;

            public AddCallbackFn(Queue workQueue)
            {
                System.Console.WriteLine("[AddCallbackFn] ctor");
                this.workQueue = workQueue;
            }

            public override object invoke(object callbackfn)
            {
                System.Console.WriteLine("[AddCallbackFn] invoke {0}", callbackfn);
                workQueue.Enqueue(callbackfn);
                return null;
            }
        }
    };

}