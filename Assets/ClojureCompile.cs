using UnityEngine;
using System.IO;
using System.Collections;
using clojure.lang;

public class ClojureCompile : MonoBehaviour {
	public string cljName;

	void Start () {
		Var.pushThreadBindings(RT.map(
            Compiler.CompilePathVar, "/Users/nasser/Scratch/sexant/Assets/Clojure/",
            RT.WarnOnReflectionVar, false,
            RT.UncheckedMathVar, false,
            Compiler.CompilerOptionsVar, null
        ));

	    Debug.Log("Compiling " + cljName);
	    Debug.Log(Compiler.CompileVar);
	    Compiler.CompileVar.invoke(Symbol.intern(cljName));
	    Debug.Log("Done Compiling " + cljName);
	    // PureClojureBehaviour p = new PureClojureBehaviour();
	    // Debug.Log(p.GetType().BaseType);
	    // p.Start();
	    // p.Update();
	    // gameObject.AddComponent<PureClojureBehaviour>();
	}
}