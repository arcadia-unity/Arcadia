using System;
using System.Collections;
using clojure.lang;
using UnityEngine;

namespace Arcadia
{
    public class NReplBootstrap : MonoBehaviour
    {
        static Queue WorkQueue = Queue.Synchronized(new Queue());

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

        public int port = 3723;
        private void Awake()
        {
            var addCallbackIFn = new AddCallbackFn(WorkQueue);
            System.Console.WriteLine("[nrepl] bootstrap awake, callback fn: {0} port: {1}", addCallbackIFn, port);
            NRepl.StartServer(addCallbackIFn, port);
        }

        private void Update()
        {
            System.Console.WriteLine("[nrepl] bootstrap update");
            if(WorkQueue.Count > 0)
            {
                System.Console.WriteLine("[nrepl] dequeueing {0} items", WorkQueue.Count);
                var workItems = WorkQueue.ToArray();
                WorkQueue.Clear();
                foreach (var workItem in workItems)
                {
                    var cb = workItem as IFn;
                    if(cb != null)
                    {
                        System.Console.WriteLine("[nrepl] invoking {0}", cb);
                        cb.invoke();
                    }
                    else
                    {
                        System.Console.WriteLine("[nrepl] not invoking {0}", workItem);
                    }
                }
            }
        }

        private const string HostObjectName = "##<Arcadia-NRepl-Host-Object>##";

        public static void StartRepl()
        {
            System.Console.WriteLine("[nrepl] StartRepl called");
            var go = GameObject.Find(HostObjectName);
            System.Console.WriteLine("[nrepl] searching for {0}...", HostObjectName);
            if (go != null) {
                System.Console.WriteLine("[nrepl] found {0}, not recreating", HostObjectName);
                return;
            }
            System.Console.WriteLine("[nrepl] did not find {0} creating now", HostObjectName);
            go = new GameObject(HostObjectName);
            go.AddComponent<NReplBootstrap>();
            DontDestroyOnLoad(go);
            System.Console.WriteLine("[nrepl] creating {0}", go);
        }
    }
}