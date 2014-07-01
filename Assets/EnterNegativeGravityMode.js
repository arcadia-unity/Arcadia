
function Update () {
  if(Input.GetKeyDown("space")) {
    GetComponent(WeirdGravity).gravity.y = 30;
  }
}