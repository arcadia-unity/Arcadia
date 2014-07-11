using UnityEngine;
using System.Collections;

public class PathFollowerCSharp : MonoBehaviour {
  Vector3[] state = new Vector3[1000];

  void Start () {
    for(int i=0; i<state.Length; i++) {
      state[i] = new Vector3(
        (int)Random.Range(0, 30), 
        (int)Random.Range(0, 18),
        0);
    }
  }
  
  // Update is called once per frame
  void Update () {
    Vector3 currentpos = state[(int)Time.time];
    Vector3 nextpos = state[(int)Time.time+1];

    GetComponent<Transform>().localPosition = Vector3.Lerp(
      currentpos,
      nextpos,
      (Time.time - (int)Time.time));
  }
}
