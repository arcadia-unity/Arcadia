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

  string output = "Clojure Async REPL v0.1 (sexant)\n";
  Queue<CodeAndSocket> incomingLines = new Queue<CodeAndSocket>();
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
    incomingLines.Enqueue(new CodeAndSocket(code, socket));
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
    while(incomingLines.Count > 0) {
      CodeAndSocket cas = incomingLines.Dequeue();
      string line = cas.code;
      Socket socket = cas.socket;

      try {
        var result = RT.var("unityRepl", "repl-eval-string").invoke(line, new AsyncReplTextWriter(socket));
        byte[] byteData = Encoding.ASCII.GetBytes((result == null ? "nil" : result.ToString()) + "\x04");
        socket.BeginSend(byteData, 0, byteData.Length, 0, (ar) => Debug.Log("Sent " + socket.EndSend(ar) + " bytes"), socket);

        output = "==> " + line + "\n" + result + "\n";
        Repaint();
      } catch(System.Exception e) {
        Debug.LogException(e);
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