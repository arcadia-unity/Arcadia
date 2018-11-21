using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading;
using BencodeNET;
using BencodeNET.Exceptions;
using BencodeNET.Objects;
using UnityEngine;
using clojure.lang;

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
                var code = _request["code"].ToString();

                // TODO try catch, send back exceptions
                var form = readStringVar.invoke(code);
                var result = evalVar.invoke(form);
                var output = (string) prStrVar.invoke(result);
                
                NRepl.SendMessage(new BDictionary
                {
                    {"id", _request["id"]},
                    {"status", new BList {"done"}}, // TODO does this have to be a list?
                    {"value", output}, // TODO do we need :values?
                    {"ns", "user"}, // TODO get actual *ns*
                    {"session", "dummy-session"}, // TODO real sessions
                }, _client);
                
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
                        var response = new BDictionary
                        {
                            {"id", message["id"]},
                            {"status", new BList {"done"}},
                            {"new-session", "dummy-session"}
                        };
                        SendMessage(response, client);
                        break;
                    case "eval":
                        var fn = new EvalFn(message, client);
                        addCallbackVar.invoke(fn);
                        break;
                    // TODO "describe"
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
                                            HandleMessage(message, client);
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