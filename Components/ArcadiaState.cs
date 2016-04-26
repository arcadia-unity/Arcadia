using UnityEngine;
using System.Collections;
using clojure.lang;

public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
  [TextArea(3,10)]
  public string edn;
  public object state;
  
  static IFn prStr;
  static IFn readString;
  
  static ArcadiaState()
  {
    prStr = (IFn)RT.var("clojure.core", "pr-str");
    readString = (IFn)RT.var("clojure.core", "read-string");
  }
  
  public void OnBeforeSerialize()
  {
    edn = (string)prStr.invoke(state);
  }
  
  public void OnAfterDeserialize()
  {
    state = (object)readString.invoke(edn);
  }
}
