using UnityEngine;
using UnityEditor;
using clojure.lang;
using System;
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

// State object for reading client data asynchronously
public class StateObject {
    // Client  socket.
    public Socket workSocket = null;
    // Size of receive buffer.
    public const int BufferSize = 1024;
    // Receive buffer.
    public byte[] buffer = new byte[BufferSize];
    // Received data string.
    public StringBuilder sb = new StringBuilder();  
}

public class AsynchronousSocketListener {
    public delegate void OnSendDataAction(int bytes, Socket socket);
    public event OnSendDataAction OnSendData;

    public delegate void OnGetDataAction(string content, int length, Socket socket);
    public event OnGetDataAction OnGetData;

    public delegate void OnConnectAction(Socket socket);
    public event OnConnectAction OnConnect;

    public delegate void OnDisconnectAction(Socket socket);
    public event OnDisconnectAction OnDisconnect;

    // Thread signal.
    public ManualResetEvent allDone = new ManualResetEvent(false);

    public Socket listener;
    volatile bool running = false;

    public void StopListening() {
        running = false;

        listener.Close();
    }

    public void StartListening(int port=11211) {
        // Data buffer for incoming data.
        byte[] bytes = new Byte[1024];

        // Establish the local endpoint for the socket.
        // The DNS name of the computer
        // running the listener is "host.contoso.com".
        IPHostEntry ipHostInfo = Dns.GetHostEntry("localhost");
        IPAddress ipAddress = ipHostInfo.AddressList[0];
        IPEndPoint localEndPoint = new IPEndPoint(ipAddress, port);

        // Bind the socket to the local endpoint and listen for incoming connections.
        try {
            // Create a TCP/IP socket.
            listener = new Socket(AddressFamily.InterNetwork,
            SocketType.Stream, ProtocolType.Tcp );

            listener.Bind(localEndPoint);
            listener.Listen(100);

            running = true;
            while (running) {
                // Set the event to nonsignaled state.
                allDone.Reset();

                // Start an asynchronous socket to listen for connections.
                listener.BeginAccept( 
                    AcceptCallback,
                    listener );

                // Wait until a connection is made before continuing.
                allDone.WaitOne();
            }

        } catch(ThreadAbortException e) {
            //listener.OnGetData -= GetData;
            //listener.StopListening();
            //thread.Join();

        } catch (Exception e) {
            Debug.Log(e.ToString());
        }
    }

    public void AcceptCallback(IAsyncResult ar) {
        // Signal the main thread to continue.
        allDone.Set();

        // Get the socket that handles the client request.
        Socket listener = (Socket) ar.AsyncState;
        Socket handler = listener.EndAccept(ar);

        OnConnect(handler);

        // Create the state object.
        StateObject state = new StateObject();
        state.workSocket = handler;
        handler.BeginReceive( state.buffer, 0, StateObject.BufferSize, 0,
            ReceiveCallback, state);
    }

    public void ReceiveCallback(IAsyncResult ar) {
        String content = String.Empty;
        
        // Retrieve the state object and the handler socket
        // from the asynchronous state object.
        StateObject state = (StateObject) ar.AsyncState;
        Socket handler = state.workSocket;

        // Read data from the client socket. 
        try {
            int bytesRead = handler.EndReceive(ar);

            if (bytesRead > 0) {
                // There  might be more data, so store the data received so far.
                state.sb.Append(Encoding.ASCII.GetString(
                    state.buffer,0,bytesRead));

                content = state.sb.ToString();
                if(OnGetData != null)
                    OnGetData(content, content.Length, state.workSocket);
                state.sb.Remove(0, content.Length);

                // always get more data
                handler.BeginReceive(state.buffer, 0, StateObject.BufferSize, 0,
                ReceiveCallback, state);                

            } else {
                OnDisconnect(handler);
                handler.Shutdown(SocketShutdown.Both);
                handler.Close();

            }

        } catch (Exception e) {
            handler.Shutdown(SocketShutdown.Both);
            handler.Close();
            Debug.LogException(e);

        }
    }
}