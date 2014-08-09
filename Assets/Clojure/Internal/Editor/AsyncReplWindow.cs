using UnityEngine;
using UnityEditor;
using clojure.lang;
using System.Net;
using System.Text;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Threading;

public class AsyncReplWindow : EditorWindow {
  [MenuItem ("Clojure/REPL/Window...")]
  static void Init () {
    AsyncReplWindow window = (AsyncReplWindow)EditorWindow.GetWindow (typeof (AsyncReplWindow));
  }

  [MenuItem ("Clojure/REPL/Start %#r")]
  static void StartREPL () {
    AsyncRepl.StartListening();
    EditorApplication.update += AsyncRepl.Update;
    Debug.Log("Started Clojure REPL");
  }

  [MenuItem ("Clojure/REPL/Stop &#r")]
  static void StopREPL () {
    AsyncRepl.StopListening();
    EditorApplication.update -= AsyncRepl.Update;
    Debug.Log("Stopped Clojure REPL");
  }

  void OnGUI () {
    if(AsyncRepl.running) {
      GUI.color = Color.red;
      if(GUILayout.Button("Stop REPL")) {
        AsyncReplWindow.StopREPL();
      }

      IPEndPoint endPoint = AsyncRepl.listener.listener.LocalEndPoint as IPEndPoint;
      GUILayout.Label("REPL is listening on " + endPoint.Address.ToString() + ":" + endPoint.Port + "\n" + AsyncRepl.output);

    } else {
      GUI.color = Color.green;
      if(GUILayout.Button("Start REPL")) {
        AsyncReplWindow.StartREPL();
      }

      GUILayout.Label("REPL is not running");
    }
  }
}

public class AsyncRepl {
  struct CodeAndSocket {
    public string code;
    public Socket socket;

    public CodeAndSocket(string code, Socket socket) {
      this.code = code;
      this.socket = socket;
    }
  }

  public static bool running = false;
  public static AsynchronousSocketListener listener;
  public static string output = "";

  static Queue<Socket> updatedSockets = new Queue<Socket>();
  static Dictionary<Socket, string> socketCode = new Dictionary<Socket, string>();
  static Thread thread;

  public static void StartListening() {
    StopListening();
    
    RT.load("unityRepl");
    listener = new AsynchronousSocketListener();
    listener.OnGetData += GetData;
    listener.OnConnect += (s) => Debug.Log("OnConnect");

    thread = new Thread(() => listener.StartListening());
    thread.Start();

    running = true;
  }

  static void GetData(string code, int length, Socket socket) {
    if(running) {
      if(!socketCode.ContainsKey(socket))
        socketCode[socket] = "";

      socketCode[socket] += code;
      updatedSockets.Enqueue(socket);
    } else {
      socket.Shutdown(SocketShutdown.Both);
    }
  }

  public static void StopListening() {
    if(listener != null) {
      listener.OnGetData -= GetData;
      listener.StopListening();
      thread.Join();
    }

    output = "";

    running = false;
  }

  public static void Update() {
    while(updatedSockets.Count > 0) {
      Socket socket = updatedSockets.Dequeue();
      if(!socketCode.ContainsKey(socket))
        continue;
      string code = socketCode[socket];

      try {
        var result = RT.var("unityRepl", "repl-eval-string").invoke(code, new AsyncReplTextWriter(socket));
        byte[] byteData = Encoding.ASCII.GetBytes(System.Convert.ToString(result));
        socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => { }, socket);

        output = "--> " + code + result + "\n" + output;
        socketCode.Remove(socket);

      } catch(System.IO.EndOfStreamException e) {
        // incomplete form, wait

      } catch(System.Exception e) {
        Debug.LogException(e);
        socketCode.Remove(socket);

        byte[] byteData = Encoding.ASCII.GetBytes(e.ToString());
        socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => { }, socket);
      }
    }
  }
}


public class AsyncReplTextWriter : System.IO.TextWriter {
  Socket socket;
  public AsyncReplTextWriter(Socket s) {
    socket = s;
  }

  public override void Write(string value) {
    base.Write(value);

    byte[] byteData = Encoding.ASCII.GetBytes((value == null ? "nil" : value.ToString()));
    socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => Debug.Log("Sent " + socket.EndSend(ar) + " bytes"), socket);

    Debug.Log(value);
  }

  public override Encoding Encoding {
    get { return System.Text.Encoding.ASCII; }
  }
}