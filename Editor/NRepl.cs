using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using BencodeNET.Exceptions;
using BencodeNET.Objects;
using UnityEngine;
using clojure.lang;

// gross, but makes errors in some REPL clients better
namespace java.io
{
    public class FileNotFoundException : System.IO.FileNotFoundException
    {
    }
}

namespace Arcadia
{
    public class NRepl
    {
        private static Var readStringVar;
        private static Var evalVar;
        private static Var prStrVar;
        private static Var addCallbackVar;

        static NRepl()
        {
            Util.require("arcadia.internal.editor-callbacks");

            addCallbackVar = RT.var("arcadia.internal.editor-callbacks", "add-callback");
            readStringVar = RT.var("clojure.core", "read-string");
            evalVar = RT.var("clojure.core", "eval");
            prStrVar = RT.var("clojure.core", "pr-str");
        }

        private static ConcurrentDictionary<Guid, Associative>
            _sessions = new ConcurrentDictionary<Guid, Associative>();

        private static Associative DefaultBindings =>
            RT.map(
                RT.CurrentNSVar, Namespace.findOrCreate(Symbol.intern("user")),
                RT.UncheckedMathVar, false,
                RT.WarnOnReflectionVar, false,
                RT.MathContextVar, null);

        static Guid NewSession() => NewSession(DefaultBindings);

        static Guid NewSession(Associative bindings)
        {
            var newGuid = Guid.NewGuid();
            _sessions.GetOrAdd(newGuid, bindings);
            return newGuid;
        }

        static Associative UpdateSession(Guid session, Associative newBindings)
        {
            return _sessions.AddOrUpdate(session, newBindings, (guid, associative) => newBindings);
        }

        static Guid CloneSession(Guid originalGuid)
        {
            return NewSession(_sessions[originalGuid]);
        }
        
        static Guid GetSession(BDictionary message)
        {
            Guid session;
            if (message.ContainsKey("session"))
                session = Guid.Parse(message["session"].ToString());
            else
                session = NewSession();
            return session;
        }

        class EvalFn : AFn
        {
            private BDictionary _request;
            private TcpClient _client;

            public EvalFn(BDictionary request, TcpClient client)
            {
                _request = request;
                _client = client;
            }

            public override object invoke()
            {
                var session = GetSession(_request);
                var code = _request["code"].ToString();
                var sessionBindings = _sessions[session];
                var outWriter = new StringWriter();
                

                Var.pushThreadBindings(sessionBindings);
                try
                {
                    var form = readStringVar.invoke(code);
                    var result = evalVar.invoke(form);
                    var value = (string) prStrVar.invoke(result);
                    outWriter.Flush();
                    var outString = outWriter.ToString();

                    UpdateSession(session, Var.getThreadBindings());
                    

                    SendMessage(new BDictionary
                    {
                        {"id", _request["id"]},
                        {"status", new BList {"done"}}, // TODO does this have to be a list?
                        {"value", value}, // TODO do we need :values?
                        {"ns", RT.CurrentNSVar.deref().ToString()},
                        {"out", outString},
                        {"session", session.ToString()},
                    }, _client);
                }
                catch (Exception e)
                {
                    SendMessage(new BDictionary
                    {
                        {"id", _request["id"]},
                        {"status", "eval-error"},
                        {"ex", e.GetType().ToString()},
                        {"err", e.Message},
                        {"ns", RT.CurrentNSVar.deref().ToString()},
                        {"session", session.ToString()},
                    }, _client);

                    throw;
                }
                finally
                {
                    Var.popThreadBindings();
                }

                return null;
            }
        }

        public static bool running = false;

        public static void SendMessage(BDictionary message, TcpClient client)
        {
            var bytes = message.EncodeAsBytes();
            client.GetStream().Write(bytes, 0, bytes.Length);
        }

        static void HandleMessage(BDictionary message, TcpClient client)
        {
            var opValue = message["op"];
            var opString = opValue as BString;
            if (opString != null)
            {
                switch (opString.ToString())
                {
                    case "clone":
                        var session = GetSession(message);
                        var newSession = CloneSession(session);
                        SendMessage(
                            new BDictionary
                            {
                                {"id", message["id"]},
                                {"status", new BList {"done"}},
                                {"new-session", newSession.ToString()}
                            }, client);
                        break;
                    case "describe":
                        // TODO include arcadia version 
                        var clojureVersion = (IPersistentMap) RT.var("clojure.core", "*clojure-version*").deref();
                        var clojureMajor = (int) clojureVersion.valAt(Keyword.intern("major"));
                        var clojureMinor = (int) clojureVersion.valAt(Keyword.intern("minor"));
                        var clojureIncremental = (int) clojureVersion.valAt(Keyword.intern("incremental"));
                        var clojureQualifier = (string) clojureVersion.valAt(Keyword.intern("qualifier"));
                        SendMessage(
                            new BDictionary
                            {
                                {"id", message["id"]},
                                {"status", new BList {"done"}},
                                {
                                    "ops",
                                    new BDictionary
                                    {
                                        {"eval", 1},
                                        {"describe", 1},
                                        {"clone", 1},
                                    }
                                },
                                {
                                    "versions",
                                    new BDictionary
                                    {
                                        {
                                            "clojure", new BDictionary
                                            {
                                                {"major", clojureMajor},
                                                {"minor", clojureMinor},
                                                {"incremental", clojureIncremental},
                                                {"qualifier", clojureQualifier}
                                            }
                                        },
                                        {
                                            "nrepl", new BDictionary
                                            {
                                                {"major", 0},
                                                {"minor", 2},
                                                {"incremental", 3}
                                            }
                                        }
                                    }
                                }
                            }, client);
                        break;
                    case "eval":
                        var fn = new EvalFn(message, client);
                        addCallbackVar.invoke(fn);
                        break;
                }
            }
        }

        public static void StopServer()
        {
            running = false;
        }

        public static void StartServer()
        {
            running = true;
            new Thread(() =>
            {
                // TODO timeout and respond to StopServer
                var listener = new TcpListener(IPAddress.Any, 9999);
                listener.Start();
                Debug.LogFormat("nrepl: listening on port {0}", 9999);
                while (running)
                {
                    // TODO timeout and respond to StopServer
                    var client = listener.AcceptTcpClient();
                    new Thread(() =>
                    {
                        Debug.LogFormat("nrepl: connected to client {0}", client.Client.RemoteEndPoint);
                        var parser = new BencodeNET.Parsing.BencodeParser();
                        var buffer = new byte[1024 * 8]; // 8k buffer
                        while (running)
                        {
                            // bencode needs a seekable stream to parse, so each
                            // message gets its own MemoryStream (MemoryStreams are
                            // seekable, NetworkStreams e.g. client.GetStream() are not)
                            using (var ms = new MemoryStream())
                            {
                                // message might be bigger than our buffer
                                // loop till we have the whole thing
                                var parsedMessage = false;
                                while (!parsedMessage)
                                {
                                    // copy from network stream into memory stream
                                    var total = client.GetStream().Read(buffer, 0, buffer.Length);
                                    ms.Write(buffer, 0, total);
                                    // bencode parsing expects stream position to be 0
                                    ms.Position = 0;
                                    try
                                    {
                                        // try and parse the message and handle it
                                        var obj = parser.Parse(ms);
                                        parsedMessage = true;
                                        var message = obj as BDictionary;
                                        if (message != null)
                                        {
                                            try
                                            {
                                                HandleMessage(message, client);
                                            }
                                            catch (Exception e)
                                            {
                                                Debug.LogException(e);
                                            }
                                        }
                                    }
                                    catch (InvalidBencodeException<BDictionary> e)
                                    {
                                        // most likely an incomplete message. i kind
                                        // of wish this was an EOF exception... we cannot
                                        // actually tell the difference between an incomplete
                                        // message and an invalid one as it stands

                                        // seek to the end of the MemoryStream to take on more bytes
                                        ms.Seek(0, SeekOrigin.End);
                                    }
                                }
                            }
                        }

                        Debug.LogFormat("nrepl: disconnected from client {0}", client.GetHashCode());
                    }).Start();
                }

                Debug.LogFormat("nrepl: closing port {0}", 9999);
            }).Start();
        }
    }
}