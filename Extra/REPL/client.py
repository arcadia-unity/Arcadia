#!/usr/bin/python

import asyncore
import logging
import threading
import collections
import re
import sys

def qwik_encode(s):
    return s + "\x04"

def qwik_decode(s):
    return s.strip("\x04")

def has_eof(s):
    return "\x04" in s

class ReplClient(asyncore.dispatcher):
    def __init__(self, host_, port_, chunk_size=1024):
        self.host = host_
        self.port = port_
        self.to_send = collections.deque()
        self.incoming = ""
        self.logger = logging.getLogger('ReplClient')
        self.received_data = []
        self.chunk_size = chunk_size
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.logger.debug('connecting to %s', (host_, port_))
        self.connect((host_, port_))

    # this is stupid. delete, in the infinite future
    def handle_connect(self): 
        self.logger.debug('hit handle_connect()')
    
    def handle_close(self):
        self.logger.debug('hit handle_close()')
        self.close()
    
    def writable(self):
        self.logger.debug('hit writable()')
        return bool(self.to_send)

    def handle_write(self):
        self.logger.debug('hit handle_write()')
        msg = self.to_send.pop()
        # sendable_msg = qwik_encode(msg[:self.chunk_size])
        # rest_msg = msg[self.chunk_size:]
        print repr("sending " + qwik_encode(msg))
        sent = self.send(qwik_encode(msg))
        # if bool(rest_msg):
            # self.to_send.appendleft(rest_msg)

    def handle_read(self):
        self.logger.debug('hit handle_read()')
        data = self.recv(self.chunk_size)
        self.incoming += data # don't think we have to ascii decode first level
        if(has_eof(self.incoming)):
            print qwik_decode(self.incoming)
            self.incoming = ""
        else:
            self.logger.debug('awaiting more data')
        
    def add_message(self, msg):
        self.logger.debug('hit add_message(%s)', (msg))
        self.message = msg
        self.to_send.append(msg)


def get_input():
    incoming = []
    sys.stdout.write('--> ')
    while((not incoming) or (0 < len(incoming[-1]) and incoming[-1][-1]=='\n')):
        incoming.append(sys.stdin.readline())
    return "".join(incoming)

class Input(threading.Thread):
    def __init__ (self, client):
        threading.Thread.__init__(self)

    def run(self):
        while True:
            client.add_message(get_input())

if __name__ == '__main__':
    import socket
    print "starting"
    # tcp_ip = '192.168.1.112' # or whatever
    tcp_ip = '192.168.1.112'

    tcp_port = 11000
    buffer_size = 1024

    logging.basicConfig(level=logging.DEBUG,
                        format='%(name)s: %(message)s',
                        )



    try:
        client = ReplClient(tcp_ip, tcp_port, chunk_size=buffer_size)
        Input(client).start()
        asyncore.loop()

    except (KeyboardInterrupt, SystemExit):
        sys.exit()
        