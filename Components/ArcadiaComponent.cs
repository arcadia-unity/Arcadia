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
  
  // if serializedVar not null, set fn to var
  public void OnAfterDeserialize()
  {
    if(serializedVar != "")
    {
      Symbol sym = Symbol.intern(serializedVar);
      if(sym.Namespace != null) {
        RT.var("clojure.core", "require").invoke(Symbol.intern(sym.Namespace));
        fn = RT.var(sym.Namespace, sym.Name);
      }
      // string libName = sym.Namespace.Replace(".", "/").Replace("-", "_");
      // Debug.Log("Loading " + libName);
      // RT.load(libName);
      // fn = Var.intern(Symbol.intern(sym.Namespace), Symbol.intern(sym.Name));
    }
  }
}