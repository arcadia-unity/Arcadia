using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using clojure.lang;

public class ClojureBehavior : MonoBehaviour {
	public string path; // "clojure/moves"
	public bool[] someBools;

	Var updatefn;

	void Start () {
		Debug.Log(Query.All(o => true));
		RT.load("clojure.core");
		RT.load(path);

		RT.var("clojure.unity", "setup").invoke(gameObject);
		updatefn = RT.var("clojure.unity", "update");

		Debug.Log("foo");
	}
	
	void Update () {
		updatefn.invoke(gameObject);
		// transform.Rotate(0, 0, 4);
	}
}
