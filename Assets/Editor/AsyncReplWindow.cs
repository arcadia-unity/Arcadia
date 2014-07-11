using UnityEngine;
using UnityEditor;
using clojure.lang;
using System.Net;
using System.Text;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Threading;

public class AsyncReplWindow : EditorWindow {
  struct CodeAndSocket {
    public string code;
    public Socket socket;

    public CodeAndSocket(string code, Socket socket) {
      this.code = code;
      this.socket = socket;
    }
  }

  string output = "";
  Queue<Socket> updatedSockets = new Queue<Socket>();
  Dictionary<Socket, string> socketCode = new Dictionary<Socket, string>();
  AsynchronousSocketListener listener;
  Thread thread;

  [MenuItem ("Window/Clojure Async REPL")]
  static void Init () {
    AsyncReplWindow window = (AsyncReplWindow)EditorWindow.GetWindow (typeof (AsyncReplWindow));
    window.StartListening();
  }

  public void StartListening() {
    RT.load("unityRepl");
    listener = new AsynchronousSocketListener();
    listener.OnGetData += GetData;
    listener.OnConnect += (s) => Debug.Log("OnConnect");

    thread = new Thread(() => listener.StartListening());
    thread.Start();
  }

  void GetData(string code, int length, Socket socket) {
    if(!socketCode.ContainsKey(socket))
      socketCode[socket] = "";

    socketCode[socket] += code;
    updatedSockets.Enqueue(socket);
  }

  void OnDestroy() {
    if(listener != null) {
      listener.OnGetData -= GetData;
      listener.StopListening();
      thread.Join();
    }
  }

  void OnGUI () {
    if(GUILayout.Button("Write")) {
      System.Console.Write("Hello!");
    }

    GUILayout.TextArea(output, 500);
  }

  void Update() {
    while(updatedSockets.Count > 0) {
      Socket socket = updatedSockets.Dequeue();
      if(!socketCode.ContainsKey(socket))
        continue;
      string code = socketCode[socket];

      try {
      	Debug.Log("Incoming: " + code);
        var result = RT.var("unityRepl", "repl-eval-string").invoke(code, new AsyncReplTextWriter(socket));
        byte[] byteData = Encoding.ASCII.GetBytes(System.Convert.ToString(result));
        socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => Debug.Log("Sent " + socket.EndSend(ar) + " bytes"), socket);

        output = "--> " + code + "\n" + result + "\n";
        Repaint();
        socketCode.Remove(socket);

      } catch(System.IO.EndOfStreamException e) {
        Debug.Log("Incomplete! " + code);

      } catch(System.Exception e) {
        Debug.LogException(e);
        socketCode.Remove(socket);

        byte[] byteData = Encoding.ASCII.GetBytes(e.ToString() + "\x04");
        socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => Debug.Log("Sent " + socket.EndSend(ar) + " bytes"), socket);
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