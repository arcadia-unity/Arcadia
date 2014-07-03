using UnityEngine;
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

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

    Socket listener;
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

        // Create a TCP/IP socket.
        listener = new Socket(AddressFamily.InterNetwork,
            SocketType.Stream, ProtocolType.Tcp );

        // Bind the socket to the local endpoint and listen for incoming connections.
        try {
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

        } catch (Exception e) {
            Debug.LogException(e);
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
                int lastEOT = content.LastIndexOf("\x04");
                if(lastEOT != -1) {
                    // found EOT, process each chunk and remove from state
                    foreach(string chunk in content.Substring(0, lastEOT).Split(new char[] { '\x04' })) {
                        Debug.Log(chunk);
                        if(chunk.Length == 0) continue;
                        OnGetData(chunk, chunk.Length, state.workSocket);
                    }

                    state.sb.Remove(0, lastEOT);
                }

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