using clojure.lang;
using System;
using System.Collections;
using System.Net;
using UnityEngine;
using Arcadia.Ifrit;

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
                FileConsole.Log("[socket-repl] creating {0}", go);
            }
        }

        public void SetupIfrit()
        {
            FileConsole.Log("[socket repl] Entering SetupIfrit");
            var setupNsName = "arcadiatech.intervention-api.setup";
            Arcadia.Util.require(setupNsName);
            RT.var(setupNsName, "setup").invoke(this.gameObject);
            FileConsole.Log("[socket repl] Exiting SetupIfrit");
        }

        static void DoInit()
        {
            FileConsole.Log("[socket-repl] entering DoInit");
            if (didInit == false)
            {
                FileConsole.Log("[socket-repl] entering DoInit body");
                didInit = true;
                callbackDriverKeyword = Keyword.intern("callback-driver");
                portKeyword = Keyword.intern("port");
                argsKeyword = Keyword.intern("args");
                Arcadia.Util.require("arcadia.internal.socket-repl");
                startServerVar = RT.var("arcadia.internal.socket-repl", "set-options-and-start-server");
            }
            FileConsole.Log("[socket-repl] exiting DoInit");
        }

        private void Awake()
        {
            FileConsole.Log("[socket repl] Entering Awake");
            DoInit();
            var addCallbackIFn = new AddCallbackFn(WorkQueue);
            System.Console.WriteLine("[socket-repl] bootstrap awake, callback fn: {0} port: {1}, addr: {2}", addCallbackIFn, port, IPAddress.Any);
            var optionsMap = RT.mapUniqueKeys(
                portKeyword, port,
                argsKeyword, RT.vector(addCallbackIFn)
            );
            startServerVar.invoke(optionsMap);
            SetupIfrit();
            FileConsole.Log("[socket repl] Exiting Awake SetupIfrit");
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
                        try
                        {
                            cb.invoke();
                        } 
                        catch (Exception e)
                        {
                            FileConsole.Log("[socket-repl] Exception encountered in work queue:\n{0}", e);
                        }
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