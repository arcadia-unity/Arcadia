require "socket"

s = TCPSocket.new "localhost", 11211

$/ = "\x04"

while true
  print "--> "
  input = $stdin.gets.strip
  s.write input + "\x04"
  puts s.recv 1024
end

at_exit do
  s.close
end