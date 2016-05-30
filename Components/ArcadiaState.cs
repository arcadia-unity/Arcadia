using UnityEngine;
using System.Collections;
using clojure.lang;

public class ArcadiaState : MonoBehaviour, ISerializationCallbackReceiver
{
  public string edn = "{}";
  public Atom state = new Atom(PersistentHashMap.EMPTY);
  
  static IFn prStr;
  static IFn readString;
  
  static ArcadiaState()
  {
    prStr = (IFn)RT.var("clojure.core", "pr-str");
    readString = (IFn)RT.var("clojure.core", "read-string");
  }
  
  public void OnBeforeSerialize()
  {
    edn = (string)prStr.invoke(state.deref());
  }
  
  public void OnAfterDeserialize()
  {
    state = new Atom((object)readString.invoke(edn));
  }
}
