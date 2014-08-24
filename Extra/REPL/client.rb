require "socket"
require "io/wait"

$s = TCPSocket.new "localhost", 11211
$input = ""

def balanced? str
  s = str.clone
  s.gsub! /[^\(\)\[\]\{\}]/, ""
  until s.gsub!(/\(\)|\[\]|\{\}/, "").nil?; end
  s.empty?
end

def repl_print strip_nil=false
  $s.wait
  out = $s.recv($s.nread)
  puts strip_nil ? out.gsub(/\s*nil$/, "\n\n") : out
  $prompt = "--> "
  $input = ""
end

$s.write DATA.read.strip
repl_print true

while true
  if $prompt
    print $prompt 
    $stdout.flush
  end
  
  $input += $stdin.gets

  if balanced?($input)
    $s.write $input
    repl_print
  else
    $prompt = false
  end
end

at_exit do
  $s.close
end

__END__
(do (println "; Clojure Unity REPL")
    (println (str "; Clojure " (clojure-version)))
    (println (str "; Unity " (UnityEditorInternal.InternalEditorUtility/GetFullUnityVersion)))
    (println (str "; Mono " (.Invoke (.GetMethod Mono.Runtime "GetDisplayName" (enum-or BindingFlags/NonPublic BindingFlags/Static)) nil nil))))