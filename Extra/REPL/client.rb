require "socket"

s = TCPSocket.new ARGV.first, ARGV.last.to_i

$/ = "\x04"

while true
  print "--> "
  input = $stdin.gets.strip
  s.write input + "\x04"
  puts s.recv(1024).gsub "\x04", ""
end

at_exit do
  s.close
end
