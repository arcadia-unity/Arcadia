using UnityEngine;
using clojure.lang;

public class ArcadiaBehaviour : MonoBehaviour, ISerializationCallbackReceiver
{
  private IFn _fn;
  public IFn fn
  {
    get { return _fn; }
    set
    {
      _fn = value;
      serializedVar = null;
      OnBeforeSerialize();
    }
  }
  
  [SerializeField]
  public string serializedVar;
  
  // if fn is a var, store in serializedVar 
  public void OnBeforeSerialize()
  {
    Var v = fn as Var;
    if(v != null)
    {
      serializedVar = v.Namespace.Name + "/" + v.Symbol.Name;
    }
  }

  public void OnAfterDeserialize() {
#if UNITY_EDITOR  
    Awake();
#endif
  }
  
  private static IFn requireFn = null;

  // if serializedVar not null, set fn to var
  public void Awake()
  {
    if(requireFn == null) requireFn = RT.var("clojure.core", "require");
    if(serializedVar != "")
    {
      Symbol sym = Symbol.intern(serializedVar);
      if(sym.Namespace != null) {
        requireFn.invoke(Symbol.intern(sym.Namespace));
        fn = RT.var(sym.Namespace, sym.Name);
      }
    }
  }
} 