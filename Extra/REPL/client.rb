require "socket"

s = TCPSocket.new "localhost", 11211

puts "Clojure Unity\n\n"

$/ = "\x04"

while true
  print "--> "
  $stdout.flush
  input = $stdin.gets.strip
  s.write input + "\x04"

  out = s.recv(1024)
  while out[-1] != "\x04"
    out += s.recv(1024)
  end

  puts out.gsub "\x04", ""
end

at_exit do
  s.close
end
