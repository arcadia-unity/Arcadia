using UnityEngine;
using System.Collections;

public class PathFollowerCSharp : MonoBehaviour {
  int[][] state = new int[1000][];

  void Start () {
    for(int i=0; i<state.Length; i++) {
      state[i] = new int[2];
      state[i][0] = (int)Random.Range(0, 30);
      state[i][1] = (int)Random.Range(0, 18);
    }
  }
  
  // Update is called once per frame
  void Update () {
    int[] currentpos = state[(int)Time.time];
    int[] nextpos = state[(int)Time.time+1];
    GetComponent<Transform>().localPosition = Vector3.Lerp(
      new Vector3(currentpos[0], currentpos[1], 0),
      new Vector3(nextpos[0], nextpos[1], 0),
      (Time.time - (int)Time.time));
  }
}
