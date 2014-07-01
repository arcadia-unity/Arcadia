var value : float;

function Start () {

}

function Update () {
  value = Mathf.Sin(Time.time);
  if(Input.GetKeyDown("space")) {
    Debug.Log(value);
  }
}