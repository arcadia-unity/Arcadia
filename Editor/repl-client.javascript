const dgram = require('dgram');
const readline = require('readline');

const server = dgram.createSocket('udp4');
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

const arcadiaPort = process.argv[2] || 11211;
const arcadiaHost = process.argv[3] || "localhost";

var input = "";

rl.on('line', (cmd) => {
  // always grow input
  input += cmd + "\n";
  if(parenthesesAreBalanced(input)) {
    // send balanced form to server
    server.send(input, arcadiaPort, arcadiaHost);
    // reset input
    input = "";
    // pause prompt until message event
    rl.pause();
  }
});

rl.on('close', () => {
  process.exit(0);
});

server.on('message', (msg, rinfo) => {
  var msg = msg.toString();
  var msgLines = msg.split("\n");
  
  // set last line as prompt
  rl.setPrompt(msgLines.pop());
  // display all other lines
  console.log(msgLines.join("\n"));
  // result prompt
  rl.prompt();
});

// send header form to start prompt
server.send('(binding [*warn-on-reflection* false] (do (println "; Arcadia REPL") (println (str "; Clojure " (clojure-version))) (println (str "; Unity " (UnityEditorInternal.InternalEditorUtility/GetFullUnityVersion))) (println (str "; Mono " (.Invoke (.GetMethod Mono.Runtime "GetDisplayName" (enum-or BindingFlags/NonPublic BindingFlags/Static)) nil nil)))))', arcadiaPort, arcadiaHost)

// http://codereview.stackexchange.com/questions/45991/balanced-parentheses
function parenthesesAreBalanced(s)
{
  var parentheses = "[]{}()",
    stack = [], //Parentheses stack
    i, //Index in the string
    c; //Character in the string

  for (i = 0; c = s[i++];)
  {
    var bracePosition = parentheses.indexOf(c),
      braceType;
    //~ is truthy for any number but -1
    if (!~bracePosition)
      continue;

    braceType = bracePosition % 2 ? 'closed' : 'open';

    if (braceType === 'closed')
    {
      //If there is no open parenthese at all, return false OR
      //if the opening parenthese does not match ( they should be neighbours )
      if (!stack.length || parentheses.indexOf(stack.pop()) != bracePosition - 1)
        return false;
    }
    else
    {
      stack.push(c);
    }
  }
  //If anything is left on the stack <- not balanced
  return !stack.length;
}