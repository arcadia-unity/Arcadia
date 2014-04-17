using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
// using System.Linq.Enumerable;

public class Query {
	public static GameObject[] allObjects {
		get {
			return (GameObject[])GameObject.FindObjectsOfType(typeof(GameObject));
		}
	}

	public static IEnumerable All(System.Func<GameObject,bool> f) {
		return Enumerable.Where(allObjects, f);
	}
}
