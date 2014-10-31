(ns arcadia.messages)

;; from http://docs.unity3d.com/ScriptReference/MonoBehaviour.html

;; ============================================================
;; message map
;; ============================================================
(def messages
  { 'Awake []
    'FixedUpdate []
    'LateUpdate []
    'OnAnimatorIK ['System.Int32]
    'OnAnimatorMove []
    'OnApplicationFocus ['System.Boolean]
    'OnApplicationPause ['System.Boolean]
    'OnApplicationQuit []
    'OnAudioFilterRead ['|System.Single[]| 'System.Int32]
    'OnBecameInvisible []
    'OnBecameVisible []
    'OnCollisionEnter ['UnityEngine.Collision]
    'OnCollisionEnter2D ['UnityEngine.Collision2D]
    'OnCollisionExit ['UnityEngine.Collision]
    'OnCollisionExit2D ['UnityEngine.Collision2D]
    'OnCollisionStay ['UnityEngine.Collision]
    'OnCollisionStay2D ['UnityEngine.Collision2D]
    'OnConnectedToServer []
    'OnControllerColliderHit ['UnityEngine.ControllerColliderHit]
    'OnDestroy []
    'OnDisable []
    'OnDisconnectedFromServer ['UnityEngine.NetworkDisconnection]
    'OnDrawGizmos []
    'OnDrawGizmosSelected []
    'OnEnable []
    'OnFailedToConnect ['UnityEngine.NetworkConnectionError]
    'OnFailedToConnectToMasterServer ['UnityEngine.NetworkConnectionError]
    'OnGUI []
    'OnJointBreak ['System.Single]
    'OnLevelWasLoaded ['System.Int32]
    'OnMasterServerEvent ['UnityEngine.MasterServerEvent]
    'OnMouseDown []
    'OnMouseDrag []
    'OnMouseEnter []
    'OnMouseExit []
    'OnMouseOver []
    'OnMouseUp []
    'OnMouseUpAsButton []
    'OnNetworkInstantiate ['UnityEngine.NetworkMessageInfo]
    'OnParticleCollision ['UnityEngine.GameObject]
    'OnPlayerConnected ['UnityEngine.NetworkPlayer]
    'OnPlayerDisconnected ['UnityEngine.NetworkPlayer]
    'OnPostRender []
    'OnPreCull []
    'OnPreRender []
    'OnRenderImage ['UnityEngine.RenderTexture 'UnityEngine.RenderTexture]
    'OnRenderObject []
    'OnSerializeNetworkView ['UnityEngine.BitStream 'UnityEngine.NetworkMessageInfo]
    'OnServerInitialized []
    'OnTriggerEnter ['UnityEngine.Collider]
    'OnTriggerEnter2D ['UnityEngine.Collider2D]
    'OnTriggerExit ['UnityEngine.Collider]
    'OnTriggerExit2D ['UnityEngine.Collider2D]
    'OnTriggerStay ['UnityEngine.Collider]
    'OnTriggerStay2D ['UnityEngine.Collider2D]
    'OnValidate []
    'OnWillRenderObject []
    'Reset []
    'Start []
    'Update [] })

;; ============================================================
;; protocols
;; ============================================================

(defprotocol IAwake
  "Awake is called when the script instance is being loaded."
  (Awake [this]))

(defprotocol IFixedUpdate
  "This function is called every fixed framerate frame, if the MonoBehaviour is enabled."
  (FixedUpdate [this]))

(defprotocol ILateUpdate
  "LateUpdate is called every frame, if the Behaviour is enabled."
  (LateUpdate [this]))

(defprotocol IOnAnimatorIK
  "Callback for setting up animation IK (inverse kinematics)."
  (OnAnimatorIK [this a]))

(defprotocol IOnAnimatorMove
  "Callback for processing animation movements for modifying root motion."
  (OnAnimatorMove [this]))

(defprotocol IOnApplicationFocus
  "Sent to all game objects when the player gets or loses focus."
  (OnApplicationFocus [this a]))

(defprotocol IOnApplicationPause
  "Sent to all game objects when the player pauses."
  (OnApplicationPause [this a]))

(defprotocol IOnApplicationQuit
  "Sent to all game objects before the application is quit."
  (OnApplicationQuit [this]))

(defprotocol IOnAudioFilterRead
  "If OnAudioFilterRead is implemented, Unity will insert a custom filter into the audio DSP chain."
  (OnAudioFilterRead [this a b]))

(defprotocol IOnBecameInvisible
  "OnBecameInvisible is called when the renderer is no longer visible by any camera."
  (OnBecameInvisible [this]))

(defprotocol IOnBecameVisible
  "OnBecameVisible is called when the renderer became visible by any camera."
  (OnBecameVisible [this]))

(defprotocol IOnCollisionEnter
  "OnCollisionEnter is called when this collider/rigidbody has begun touching another rigidbody/collider."
  (OnCollisionEnter [this a]))

(defprotocol IOnCollisionEnter2D
  "Sent when an incoming collider makes contact with this object's collider (2D physics only)."
  (OnCollisionEnter2D [this a]))

(defprotocol IOnCollisionExit
  "OnCollisionExit is called when this collider/rigidbody has stopped touching another rigidbody/collider."
  (OnCollisionExit [this a]))

(defprotocol IOnCollisionExit2D
  "Sent when a collider on another object stops touching this object's collider (2D physics only)."
  (OnCollisionExit2D [this a]))

(defprotocol IOnCollisionStay
  "OnCollisionStay is called once per frame for every collider/rigidbody that is touching rigidbody/collider."
  (OnCollisionStay [this a]))

(defprotocol IOnCollisionStay2D
  "Sent each frame where a collider on another object is touching this object's collider (2D physics only)."
  (OnCollisionStay2D [this a]))

(defprotocol IOnConnectedToServer
  "Called on the client when you have successfully connected to a server."
  (OnConnectedToServer [this]))

(defprotocol IOnControllerColliderHit
  "OnControllerColliderHit is called when the controller hits a collider while performing a Move."
  (OnControllerColliderHit [this a]))

(defprotocol IOnDestroy
  "This function is called when the MonoBehaviour will be destroyed."
  (OnDestroy [this]))

(defprotocol IOnDisable
  "This function is called when the behaviour becomes disabled () or inactive."
  (OnDisable [this]))

(defprotocol IOnDisconnectedFromServer
  "Called on the client when the connection was lost or you disconnected from the server."
  (OnDisconnectedFromServer [this a]))

(defprotocol IOnDrawGizmos
  "Implement OnDrawGizmos if you want to draw gizmos that are also pickable and always drawn."
  (OnDrawGizmos [this]))

(defprotocol IOnDrawGizmosSelected
  "Implement this OnDrawGizmosSelected if you want to draw gizmos only if the object is selected."
  (OnDrawGizmosSelected [this]))

(defprotocol IOnEnable
  "This function is called when the object becomes enabled and active."
  (OnEnable [this]))

(defprotocol IOnFailedToConnect
  "Called on the client when a connection attempt fails for some reason."
  (OnFailedToConnect [this a]))

(defprotocol IOnFailedToConnectToMasterServer
  "Called on clients or servers when there is a problem connecting to the MasterServer."
  (OnFailedToConnectToMasterServer [this a]))

(defprotocol IOnGUI
  "OnGUI is called for rendering and handling GUI events."
  (OnGUI [this]))

(defprotocol IOnJointBreak
  "Called when a joint attached to the same game object broke."
  (OnJointBreak [this a]))

(defprotocol IOnLevelWasLoaded
  "This function is called after a new level was loaded."
  (OnLevelWasLoaded [this a]))

(defprotocol IOnMasterServerEvent
  "Called on clients or servers when reporting events from the MasterServer."
  (OnMasterServerEvent [this a]))

(defprotocol IOnMouseDown
  "OnMouseDown is called when the user has pressed the mouse button while over the GUIElement or Collider."
  (OnMouseDown [this]))

(defprotocol IOnMouseDrag
  "OnMouseDrag is called when the user has clicked on a GUIElement or Collider and is still holding down the mouse."
  (OnMouseDrag [this]))

(defprotocol IOnMouseEnter
  "OnMouseEnter is called when the mouse entered the GUIElement or Collider."
  (OnMouseEnter [this]))

(defprotocol IOnMouseExit
  "OnMouseExit is called when the mouse is not any longer over the GUIElement or Collider."
  (OnMouseExit [this]))

(defprotocol IOnMouseOver
  "OnMouseOver is called every frame while the mouse is over the GUIElement or Collider."
  (OnMouseOver [this]))

(defprotocol IOnMouseUp
  "OnMouseUp is called when the user has released the mouse button."
  (OnMouseUp [this]))

(defprotocol IOnMouseUpAsButton
  "OnMouseUpAsButton is only called when the mouse is released over the same GUIElement or Collider as it was pressed."
  (OnMouseUpAsButton [this]))

(defprotocol IOnNetworkInstantiate
  "Called on objects which have been network instantiated with Network.Instantiate."
  (OnNetworkInstantiate [this a]))

(defprotocol IOnParticleCollision
  "OnParticleCollision is called when a particle hits a collider."
  (OnParticleCollision [this a]))

(defprotocol IOnPlayerConnected
  "Called on the server whenever a new player has successfully connected."
  (OnPlayerConnected [this a]))

(defprotocol IOnPlayerDisconnected
  "Called on the server whenever a player disconnected from the server."
  (OnPlayerDisconnected [this a]))

(defprotocol IOnPostRender
  "OnPostRender is called after a camera finished rendering the scene."
  (OnPostRender [this]))

(defprotocol IOnPreCull
  "OnPreCull is called before a camera culls the scene."
  (OnPreCull [this]))

(defprotocol IOnPreRender
  "OnPreRender is called before a camera starts rendering the scene."
  (OnPreRender [this]))

(defprotocol IOnRenderImage
  "OnRenderImage is called after all rendering is complete to render image."
  (OnRenderImage [this a b]))

(defprotocol IOnRenderObject
  "OnRenderObject is called after camera has rendered the scene."
  (OnRenderObject [this]))

(defprotocol IOnSerializeNetworkView
  "Used to customize synchronization of variables in a script watched by a network view."
  (OnSerializeNetworkView [this a b]))

(defprotocol IOnServerInitialized
  "Called on the server whenever a Network.InitializeServer was invoked and has completed."
  (OnServerInitialized [this]))

(defprotocol IOnTriggerEnter
  "OnTriggerEnter is called when the Collider other enters the trigger."
  (OnTriggerEnter [this a]))

(defprotocol IOnTriggerEnter2D
  "Sent when another object enters a trigger collider attached to this object (2D physics only)."
  (OnTriggerEnter2D [this a]))

(defprotocol IOnTriggerExit
  "OnTriggerExit is called when the Collider other has stopped touching the trigger."
  (OnTriggerExit [this a]))

(defprotocol IOnTriggerExit2D
  "Sent when another object leaves a trigger collider attached to this object (2D physics only)."
  (OnTriggerExit2D [this a]))

(defprotocol IOnTriggerStay
  "OnTriggerStay is called once per frame for every Collider other that is touching the trigger."
  (OnTriggerStay [this a]))

(defprotocol IOnTriggerStay2D
  "Sent each frame where another object is within a trigger collider attached to this object (2D physics only)."
  (OnTriggerStay2D [this a]))

(defprotocol IOnValidate
  "This function is called when the script is loaded or a value is changed in the inspector (Called in the editor only)."
  (OnValidate [this]))

(defprotocol IOnWillRenderObject
  "OnWillRenderObject is called once for each camera if the object is visible."
  (OnWillRenderObject [this]))

(defprotocol IReset
  "Reset to default values."
  (Reset [this]))

(defprotocol IStart
  "Start is called on the frame when a script is enabled just before any of the Update methods is called the first time."
  (Start [this]))

(defprotocol IUpdate
  "Update is called every frame, if the MonoBehaviour is enabled."
  (Update [this]))