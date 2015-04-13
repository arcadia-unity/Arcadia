(ns arcadia.messages)

;; from http://docs.unity3d.com/ScriptReference/MonoBehaviour.html

;; ============================================================
;; message map
;; ============================================================
(def messages
  '{Awake []
    FixedUpdate []
    LateUpdate []
    OnAnimatorIK [System.Int32]
    OnAnimatorMove []
    OnApplicationFocus [System.Boolean]
    OnApplicationPause [System.Boolean]
    OnApplicationQuit []
    OnAudioFilterRead [|System.Single[]| System.Int32]
    OnBecameInvisible []
    OnBecameVisible []
    OnCollisionEnter [UnityEngine.Collision]
    OnCollisionEnter2D [UnityEngine.Collision2D]
    OnCollisionExit [UnityEngine.Collision]
    OnCollisionExit2D [UnityEngine.Collision2D]
    OnCollisionStay [UnityEngine.Collision]
    OnCollisionStay2D [UnityEngine.Collision2D]
    OnConnectedToServer []
    OnControllerColliderHit [UnityEngine.ControllerColliderHit]
    OnDestroy []
    OnDisable []
    OnDisconnectedFromServer [UnityEngine.NetworkDisconnection]
    OnDrawGizmos []
    OnDrawGizmosSelected []
    OnEnable []
    OnFailedToConnect [UnityEngine.NetworkConnectionError]
    OnFailedToConnectToMasterServer [UnityEngine.NetworkConnectionError]
    OnGUI []
    OnJointBreak [System.Single]
    OnLevelWasLoaded [System.Int32]
    OnMasterServerEvent [UnityEngine.MasterServerEvent]
    OnMouseDown []
    OnMouseDrag []
    OnMouseEnter []
    OnMouseExit []
    OnMouseOver []
    OnMouseUp []
    OnMouseUpAsButton []
    OnNetworkInstantiate [UnityEngine.NetworkMessageInfo]
    OnParticleCollision [UnityEngine.GameObject]
    OnPlayerConnected [UnityEngine.NetworkPlayer]
    OnPlayerDisconnected [UnityEngine.NetworkPlayer]
    OnPostRender []
    OnPreCull []
    OnPreRender []
    OnRenderImage [UnityEngine.RenderTexture UnityEngine.RenderTexture]
    OnRenderObject []
    OnSerializeNetworkView [UnityEngine.BitStream UnityEngine.NetworkMessageInfo]
    OnServerInitialized []
    OnTriggerEnter [UnityEngine.Collider]
    OnTriggerEnter2D [UnityEngine.Collider2D]
    OnTriggerExit [UnityEngine.Collider]
    OnTriggerExit2D [UnityEngine.Collider2D]
    OnTriggerStay [UnityEngine.Collider]
    OnTriggerStay2D [UnityEngine.Collider2D]
    OnValidate []
    OnWillRenderObject []
    Reset []
    Start []
    Update []})

;; ============================================================
;; interfaces
;; ============================================================
(defmacro emit-message-interface [message args]
  `(definterface ~(symbol (str "I" message))
     (~message ~(mapv #(vary-meta %2 assoc :tag %1)
                      args
                      (repeatedly gensym)))))

(defmacro emit-message-interfaces []
  `(do
     ~@(map (fn [[msg args]]
              `(emit-message-interface ~msg ~args))
            messages)))

;; emit an interface for every unity message, based on the message map
;; Update ==> (definterface IUpdate (Update []))
;; OnTriggerEnter ==> (definterface IOnTriggerEnter (OnTriggerEnter [^UnityEngine.Collider x]))

(emit-message-interfaces)