using UnityEngine;
using System.Collections;
using clojure.lang;

public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
  [TextArea(3,10)]
  public string edn;
  public object state;
  
  public void OnBeforeSerialize()
  {
    edn = (string)((IFn)RT.var("clojure.core", "pr-str")).invoke(state);
  }
  
  public void OnAfterDeserialize()
  {
    state = (object)((IFn)RT.var("clojure.edn", "read-string")).invoke(edn);
  }
}
