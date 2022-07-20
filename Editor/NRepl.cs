#if NET_4_6
using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using BencodeNET.Exceptions;
using BencodeNET.Objects;
using UnityEngine;
using clojure.lang;
using Microsoft.Scripting.Utils;

// shim for atom proto-repl
namespace java.io
{
	public class FileNotFoundException : System.IO.FileNotFoundException
	{
	}
}

// shim for vim-fireplace
public class GetPropertyShimFn : AFn
{
	public override object invoke (object property)
	{
		var propString = (string)property;
		switch (propString) {
		case "path.separator":
			return Path.PathSeparator.ToString();
		case "java.class.path":
			return "";
		case "fake.class.path":
			return "";
		default:
			return null;
		}
	}
}


namespace Arcadia
{
	public class NRepl
	{


		class Writer : TextWriter
		{
			private string _id;
			private string _session;
			private TcpClient _client;
			private string _kind;
			private StringBuilder _stringBuilder;

			public Writer (string kind, BDictionary request, TcpClient client)
			{
				_id = request["id"].ToString();
				_session = GetSession(request).ToString();
				_client = client;
				_kind = kind;
				_stringBuilder = new StringBuilder();
			}

			public override Encoding Encoding => Encoding.UTF8;

			public override void Write (char value)
			{
				_stringBuilder.Append(value);
			}

			public override void Flush ()
			{
				SendMessage(new BDictionary
				{
					{"id", _id},
					{_kind, _stringBuilder.ToString()},
					{"session", _session},
				}, _client);
				_stringBuilder.Clear();
			}
		}

		private static Var readStringVar;
		private static IPersistentMap readStringOptions;
		private static Var evalVar;
		private static Var prStrVar;
		private static Var addCallbackVar;
		private static Var star1Var;
		private static Var star2Var;
		private static Var star3Var;
		private static Var starEVar;
		private static Var printLevelVar;
		private static Var printLengthVar;
		private static Var errorStringVar;
		private static Var metaVar;
		private static Var nsResolveVar;
		private static Var findNsVar;
		private static Var symbolVar;
		private static Var concatVar;
		private static Var completeVar;
		private static Var configVar;

		private static Namespace shimsNS;

		static NRepl ()
		{
			string socketReplNsName = "arcadia.internal.socket-repl";
			Util.require(socketReplNsName);
			Util.getVar(ref errorStringVar, socketReplNsName, "error-string");

			Util.require("arcadia.internal.editor-callbacks");
			addCallbackVar = RT.var("arcadia.internal.editor-callbacks", "add-callback");
			readStringVar = RT.var("clojure.core", "read-string");
			evalVar = RT.var("clojure.core", "eval");
			prStrVar = RT.var("clojure.core", "pr-str");

			star1Var = RT.var("clojure.core", "*1");
			star2Var = RT.var("clojure.core", "*2");
			star3Var = RT.var("clojure.core", "*3");
			starEVar = RT.var("clojure.core", "*e");
			printLevelVar = RT.var("clojure.core", "*print-level*");
			printLengthVar = RT.var("clojure.core", "*print-length*");

			metaVar = RT.var("clojure.core", "meta");
			nsResolveVar = RT.var("clojure.core", "ns-resolve");
			findNsVar = RT.var("clojure.core", "find-ns");
			symbolVar = RT.var("clojure.core", "symbol");
			concatVar = RT.var("clojure.core", "concat");

			Util.require("arcadia.internal.nrepl-support");
			completeVar = RT.var("arcadia.internal.nrepl-support", "complete");

			Util.require("arcadia.internal.config");
			configVar = RT.var("arcadia.internal.config", "config");

			readStringOptions = PersistentHashMap.EMPTY.assoc(Keyword.intern("read-cond"), Keyword.intern("allow"));

			shimsNS = Namespace.findOrCreate(Symbol.intern("arcadia.nrepl.shims"));
			Var.intern(shimsNS, Symbol.intern("getProperty"), new GetPropertyShimFn());
			Namespace.findOrCreate(Symbol.intern("user")).addAlias(Symbol.intern("System"), shimsNS);
		}

		private static ConcurrentDictionary<Guid, Associative>
			_sessions = new ConcurrentDictionary<Guid, Associative>();

		private static Associative DefaultSessionBindings =>
			RT.map(
				RT.CurrentNSVar, Namespace.findOrCreate(Symbol.intern("user")),
				RT.UncheckedMathVar, false,
				RT.WarnOnReflectionVar, false,
				printLevelVar, null,
				printLengthVar, null,
				star1Var, null,
				star2Var, null,
				star3Var, null,
				starEVar, null,
				RT.MathContextVar, null);

		static Guid NewSession () => NewSession(DefaultSessionBindings);

		static Guid NewSession (Associative bindings)
		{
			var newGuid = Guid.NewGuid();
			_sessions.GetOrAdd(newGuid, bindings);
			return newGuid;
		}

		static Associative UpdateSession (Guid session, Associative newBindings)
		{
			return _sessions.AddOrUpdate(session, newBindings, (guid, associative) => newBindings);
		}

		static Guid CloneSession (Guid originalGuid)
		{
			return NewSession(_sessions[originalGuid]);
		}

		static Guid GetSession (BDictionary message)
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

			public EvalFn (BDictionary request, TcpClient client)
			{
				_request = request;
				_client = client;
			}

			public override object invoke ()
			{
				var session = GetSession(_request);
				var code = _request["code"].ToString();
				var sessionBindings = _sessions[session];
				var outWriter = new Writer("out", _request, _client);
				var errWriter = new Writer("err", _request, _client);
				
				// Split the path, and try to infer the ns from the filename. If the ns exists, then change the current ns before evaluating
				List<String> nsList = new List<String>();
				Namespace fileNs = null;
				try
				{
					var path = _request["file"].ToString();
					string current = null;
					while (path != null && current != "Assets")
					{
						current = Path.GetFileNameWithoutExtension(path);
						nsList.Add(current);
						path = Directory.GetParent(path).FullName;
					}
					nsList.Reverse();
					nsList.RemoveAt(0);
					// Debug.Log("Trying to find: " + string.Join(".", nsList.ToArray()));
					fileNs = Namespace.find(Symbol.create(string.Join(".", nsList.ToArray())));
					// Debug.Log("Found: " + string.Join(".", nsList.ToArray()));
				} 
				catch (Exception e)
				{ 
					/* Whatever sent in :file was not a path. Ignore it */
					// Debug.Log(":file was not a valid ns");
				}

				var evalBindings = sessionBindings
					.assoc(RT.OutVar, outWriter)
					.assoc(RT.ErrVar, errWriter);
				if (fileNs != null)
				{
					// Debug.Log("Current ns: " + fileNs.ToString());
					evalBindings = evalBindings.assoc(RT.CurrentNSVar, fileNs);
				}

				Var.pushThreadBindings(evalBindings);
				try {
					var form = readStringVar.invoke(readStringOptions, code);
					var result = evalVar.invoke(form);
					var value = (string)prStrVar.invoke(result);

					star3Var.set(star2Var.deref());
					star2Var.set(star1Var.deref());
					star1Var.set(result);

					UpdateSession(session, Var.getThreadBindings());

					SendMessage(new BDictionary
					{
						{"id", _request["id"]},
						{"value", value}, // TODO do we need :values?
                        {"ns", RT.CurrentNSVar.deref().ToString()},
						{"session", session.ToString()},
					}, _client);

					outWriter.Flush();
					errWriter.Flush();

					SendMessage(new BDictionary
					{
						{"id", _request["id"]},
						{"status", new BList {"done"}}, // TODO does this have to be a list?
                        {"session", session.ToString()},
					}, _client);
				} catch (Exception e) {
					starEVar.set(e);

					UpdateSession(session, Var.getThreadBindings());

					SendMessage(new BDictionary
					{
						{"id", _request["id"]},
						{"status", new BList {"eval-error"}},
						{"session", session.ToString()},
						{"ex", e.GetType().ToString()},
					}, _client);

					SendMessage(new BDictionary
					{
						{"id", _request["id"]},
						{"session", session.ToString()},
						{"err", errorStringVar.invoke(e).ToString()},
					}, _client);

					SendMessage(new BDictionary
					{
						{"id", _request["id"]},
						{"status", new BList {"done"}},
						{"session", session.ToString()},
					}, _client);

					throw;
				} finally {
					Var.popThreadBindings();
				}

				return null;
			}
		}

		public static bool running = false;

		public static void SendMessage (BDictionary message, TcpClient client)
		{
			var bytes = message.EncodeAsBytes();
			client.GetStream().Write(bytes, 0, bytes.Length);
		}


		static string getSymbolStr(BDictionary message)
		{
			String symbolStr = "";
			if (message.TryGetValue("symbol", out var s))
			{
				symbolStr = s.ToString();
			} else if (message.TryGetValue("sym", out var s1)) {
				symbolStr = s1.ToString();
			} else if (message.TryGetValue("prefix", out var s2)) {
				symbolStr = s2.ToString();
			}
			return symbolStr;
		}

		static void HandleMessage (BDictionary message, TcpClient client)


		{


			var opValue = message["op"];
			var opString = opValue as BString;
			var autoCompletionSupportEnabled = RT.booleanCast(((IPersistentMap)configVar.invoke()).valAt(Keyword.intern("nrepl-auto-completion")));
			if (opString != null) {
				var session = GetSession(message);
				switch (opString.ToString()) {
				case "clone":
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
					var clojureVersion = (IPersistentMap)RT.var("clojure.core", "*clojure-version*").deref();
					var clojureMajor = (int)clojureVersion.valAt(Keyword.intern("major"));
					var clojureMinor = (int)clojureVersion.valAt(Keyword.intern("minor"));
					var clojureIncremental = (int)clojureVersion.valAt(Keyword.intern("incremental"));
					var clojureQualifier = (string)clojureVersion.valAt(Keyword.intern("qualifier"));
                    var supportedOps = new BDictionary {
                        {"eval", 1},
                        {"load-file", 1},
                        {"describe", 1},
                        {"clone", 1},
                        {"info", 1},
                        {"eldoc", 1},
						{"classpath", 1},
                    };
					// Debug.Log("Autocomplete support is enabled?: " + autoCompletionSupportEnabled);
					if (autoCompletionSupportEnabled) {
						supportedOps.Add("complete", 1);
					}
					SendMessage(
						new BDictionary
						{
								{"id", message["id"]},
								{"session", session.ToString()},
								{"status", new BList {"done"}},
								{ "ops", supportedOps},
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
				case "load-file":
					message["code"] = new BString("(do " + message["file"].ToString() + " )");
					var loadFn = new EvalFn(message, client);
					addCallbackVar.invoke(loadFn);
					break;
				case "eldoc":
				case "info":

					var symbolStr = NRepl.getSymbolStr(message);
					// Editors like Calva that support doc-on-hover sometimes will ask about empty strings or spaces
					if (symbolStr == "" || symbolStr == " ") break;


					IPersistentMap symbolMetadata = null;
					try
					{
                        symbolMetadata = (IPersistentMap)metaVar.invoke(nsResolveVar.invoke(
                            findNsVar.invoke(symbolVar.invoke(message["ns"].ToString())),
                            symbolVar.invoke(symbolStr)));
					} catch (TypeNotFoundException) { 
							// We'll just ignore this call if the type cannot be found. This happens sometimes.
							// TODO: One particular case when this happens is when querying info for a namespace. 
							//       That case should be handled separately (e.g., via `find-ns`?)
						}


					if (symbolMetadata != null) {
						var resultMessage = new BDictionary {
							{"id", message["id"]},
							{"session", session.ToString()},
							{"status", new BList {"done"}}
						};

						foreach (var entry in symbolMetadata) {
							if (entry.val() != null) {
								String keyStr = entry.key().ToString().Substring(1);
								String keyVal = entry.val().ToString();

								if (keyStr == "arglists") {
									// cider expects eldoc here in this format [["(foo)"]]
									resultMessage["eldoc"] = new BList(((IEnumerable)entry.val()).Select(lst => new BList(((IEnumerable)lst).Select(o => o.ToString()))));
									resultMessage["arglists-str"] = new BString(keyVal);
								} else if (keyStr == "forms") {
									resultMessage[keyStr] = new BList(new [] { keyVal});
									resultMessage["forms-str"] = new BString(keyVal);
								} else if (keyStr == "doc") {
									resultMessage[keyStr] =  new BString(keyVal);
									resultMessage["docstring"] = new BString(keyVal);
								} else {
									resultMessage[keyStr] = new BString(keyVal);
								}

							}
						}
							SendMessage(resultMessage, client);
					} else {
							SendMessage(
								new BDictionary
								{
									{"id", message["id"]},
									{"session", session.ToString()},
									{"status", new BList {"done", "no-info"}}
								}, client);
					}
					break;
				case "complete":

					// When autoCompletionSupportEnabled is false, we don't advertise auto-completion support. 
					// some editors seem to ignore this and request anyway, so we return an unknown op message.
					if (!autoCompletionSupportEnabled) {
                        SendMessage(
                            new BDictionary
                            {
                                    {"id", message["id"]},
                                    {"session", session.ToString()},
                                    {"status", new BList {"done", "error", "unknown-op"}}
                            }, client);
						break;
					}

					Namespace ns = Namespace.find(Symbol.create(message["ns"].ToString()));
                    var sessionBindings = _sessions[session];
					var completeBindings = sessionBindings;
                    if (ns != null) {
						completeBindings = completeBindings.assoc(RT.CurrentNSVar, ns);
                    }

					// Make sure to eval this in the right namespace
					Var.pushThreadBindings(completeBindings);
					BList completions = (BList) completeVar.invoke(getSymbolStr(message));
					Var.popThreadBindings();

					SendMessage(new BDictionary
                        {
                            {"id", message["id"]},
                            {"session", session.ToString()},
                            {"status", new BList {"done"}},
							{"completions", completions}
                        }, client);
					break;
				case "classpath":
                    BList classpath = new BList();
					foreach (String p in Environment.GetEnvironmentVariable("CLOJURE_LOAD_PATH").Split(System.IO.Path.PathSeparator)) {
						if (p != "") {
                            classpath.Add(Path.GetFullPath(p));
                        }
                    }

					SendMessage(new BDictionary
                        {
                            {"id", message["id"]},
                            {"session", session.ToString()},
                            {"status", new BList {"done"}},
							{"classpath", classpath},
                        }, client);
					break;
				default:
					SendMessage(
						new BDictionary
						{
								{"id", message["id"]},
								{"session", session.ToString()},
								{"status", new BList {"done", "error", "unknown-op"}}
						}, client);
					break;
				}
			}
		}

		private const int Port = 3722;

		public static void StopServer ()
		{
			running = false;
		}

		public static void StartServer ()
		{
			Debug.Log("nrepl: starting");
			
			running = true;
			new Thread(() => {
				var listener = new TcpListener(IPAddress.Loopback, Port);
				try
				{
					// TODO make IPAddress.Loopback configurable to allow remote connections
					listener.Start();
					Debug.LogFormat("nrepl: listening on port {0}", Port);
					while (running)
					{
						if (!listener.Pending())
						{
							Thread.Sleep(100);
							continue;
						}

						var client = listener.AcceptTcpClient();
						new Thread(() =>
						{
							Debug.LogFormat("nrepl: connected to client {0}", client.Client.RemoteEndPoint);
							var parser = new BencodeNET.Parsing.BencodeParser();
							var clientRunning = true;
							var buffer = new byte[1024 * 8]; // 8k buffer
							while (running && clientRunning)
							{
								// bencode needs a seekable stream to parse, so each
								// message gets its own MemoryStream (MemoryStreams are
								// seekable, NetworkStreams e.g. client.GetStream() are not)
								try
								{
									using (var ms = new MemoryStream())
									{
										// message might be bigger than our buffer
										// loop till we have the whole thing
										var parsedMessage = false;
										while (!parsedMessage)
										{
											// copy from network stream into memory stream
											var total = client.GetStream().Read(buffer, 0, buffer.Length);
											if (total == 0)
											{
												// reading zero bytes after blocking means the other end has hung up
												clientRunning = false;
												break;
											}
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
												if (Encoding.UTF8.GetString(ms.GetBuffer())
													.Contains("2:op13:init-debugger"))
												{
													// hack to deal with cider sending us packets with duplicate keys
													// BencodeNET cannot deal with duplicate keys, hence the string check
													// the real solution is to switch to the bencode implementation that
													// nrepl itself uses
													parsedMessage = true;
												}
												else
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
								}
								catch (SocketException e)
								{
									// the other end has disconnected, gracefully shutdown
									clientRunning = false;
								}
								catch (IOException e)
								{
									// the other end has disconnected, gracefully shutdown
									clientRunning = false;
								}
								catch (ObjectDisposedException e)
								{
									// the other end has disconnected, gracefully shutdown
									clientRunning = false;
								}
								catch (Exception e)
								{
									Debug.LogWarningFormat("nrepl: {0}", e);
									clientRunning = false;
								}
							}
							Debug.LogFormat("nrepl: disconnected from client {0}", client.Client.RemoteEndPoint);
							client.Close();
							client.Dispose();
						}).Start();
					}
				}
				catch (ThreadAbortException)
				{
					// do nothing. this probably means the VM is being reset.
				}
				catch (Exception e)
				{
					Debug.LogException(e);
				}
				finally
				{
					Debug.LogFormat("nrepl: closing port {0}", Port);
					listener.Stop();
				}
				

			}).Start();
		}
	}
}
#endif