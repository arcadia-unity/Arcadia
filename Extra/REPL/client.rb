require "socket"
require "io/wait"

$s = TCPSocket.new "localhost", 11211

def repl_print wait=0.1, strip_nil=false
  if $s.wait(wait)
    out = $s.recv($s.nread)
    puts strip_nil ? out.gsub(/\s*nil$/, "\n\n") : out
    $prompt = "--> "
  else
    $prompt = "... "
  end
end

$s.write DATA.read.strip
repl_print 0.5, true

while true
  print $prompt
  $stdout.flush
  input = $stdin.gets
  $s.write input
  repl_print
end

at_exit do
  $s.close
end

__END__
(do (println "Clojure Unity REPL")
    (println (str "Clojure " (clojure-version)))
    (println (str "Unity " (UnityEditorInternal.InternalEditorUtility/GetFullUnityVersion)))
    (println (str "Mono " (.Invoke (.GetMethod Mono.Runtime "GetDisplayName" (enum-or BindingFlags/NonPublic BindingFlags/Static)) nil nil))))