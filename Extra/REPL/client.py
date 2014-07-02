#!/usr/bin/python

import asyncore
import logging
import collections
import re
import sys

# I know I know I know
def qwik_encode(s):
    return s + "\x04"

def qwik_decode(s):
    return s.strip("\x04")

def has_eof(s):
    return "\x04" in s

def input_prompt(s):
    return raw_input(s)
    
class ReplClient(asyncore.dispatcher):

    def __init__(self, host_, port_, message, chunk_size=1024):
        self.host = host_
        self.port = port_
        self.to_send = collections.deque()
        self.incoming = ""
        self.logger = logging.getLogger('ReplClient')
        self.add_message(message)
        self.received_data = []
        self.chunk_size = chunk_size
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.logger.debug('connecting to %s', (host_, port_))
        self.connect((host_, port_))
        return

    # this is stupid. delete, in the infinite future
    def handle_connect(self): 
        self.logger.debug('hit handle_connect()')
        return
    
    def handle_close(self):
        self.logger.debug('hit handle_close()')
        self.close()
        return
    
    def writable(self):
        self.logger.debug('hit writable()')
        return bool(self.to_send)

    def handle_write(self):
        self.logger.debug('hit handle_write()')
        msg = self.to_send.pop()
        sendable_msg = qwik_encode(msg[:self.chunk_size])
        rest_msg = msg[self.chunk_size:]
        sent = self.send(sendable_msg)
        if bool(rest_msg):
            self.to_send.appendleft(rest_msg)

    def handle_read(self):
        self.logger.debug('hit handle_read()')
        data = self.recv(self.chunk_size)
        self.incoming += data # don't think we have to ascii decode first level
        if(has_eof(self.incoming)):
            print qwik_decode(self.incoming)
            self.close()
            return
        else:
            self.logger.debug('awaiting more data')
            return
        
    def add_message(self, msg):
        self.logger.debug('hit add_message(%s)', (msg))
        self.message = msg
        self.to_send.append(msg)



if __name__ == '__main__':
    import socket 

    tcp_ip = '192.168.1.112' # or whatever
    tcp_port = 11000
    buffer_size = 1024

    logging.basicConfig(level=logging.DEBUG,
                        format='%(name)s: %(message)s',
                        )

    address = (tcp_ip, tcp_port)

    while(True):
        some_input = raw_input('--> ')
        client = ReplClient(tcp_ip, tcp_port, message=some_input, chunk_size=buffer_size)
        asyncore.loop()

