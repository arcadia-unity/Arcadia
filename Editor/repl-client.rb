require "io/wait"
require "socket"

$input = ""
$input.force_encoding("UTF-8")
$s = UDPSocket.new

$remote_host = ARGV[0] || "localhost"
$remote_port = ARGV[1] || 11211

def repl_send code, strip_nil=false
  #puts "repl-send 1"
  $s.send code, 0, $remote_host, $remote_port
  #puts "repl-send 2"
  $s.wait(1)
  if $s.ready?
    #puts "repl-send 3"
    out = $s.recv($s.nread)
    #puts "repl-send 4"
    print strip_nil ? out.gsub(/\s*nil$/, "\n") : out
    #puts "repl-send 5"
    $stdout.flush
  end
end

def balanced? code
  #puts "balanced 1"
  s = code.clone
  #puts "balanced 2"
  s.gsub! /[^\(\)\[\]\{\}]/, ""
  #puts "balanced 3"
  until s.gsub!(/\(\)|\[\]|\{\}/, "").nil?; end
  #puts "balanced 4"
  unless s.empty?
    #puts "UNBALANCED: #{code}\nREDUCED TO:#{s}\n\n"
  end
  s.empty?
end

repl_send DATA.read.strip, true

while true
  #puts "loop 1"
  got = $stdin.gets.force_encoding("UTF-8")
  #puts "got: #{got}" 
  $input += got
  #puts "loop 2"
  if balanced? $input
    #puts "loop 3"
    repl_send $input
    #puts "loop 4"
    $input = ""
  end
end

at_exit do
  $s.close
end

__END__
(binding [*warn-on-reflection* false]
  (do (println "; Arcadia REPL")
    (println (str "; Clojure " (clojure-version)))
    (println (str "; Unity " (UnityEditorInternal.InternalEditorUtility/GetFullUnityVersion)))
    (println (str "; Mono " (.Invoke (.GetMethod Mono.Runtime "GetDisplayName" (enum-or BindingFlags/NonPublic BindingFlags/Static)) nil nil)))))
