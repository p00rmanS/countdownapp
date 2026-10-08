"""Tiny dev server for Pawcount.
  python serve.py          -> http://localhost:5173 (this computer only)
  python serve.py --lan    -> also reachable from a phone on the same Wi-Fi
"""
import http.server
import socketserver
import sys

args = [a for a in sys.argv[1:] if not a.startswith('--')]
PORT = int(args[0]) if args else 5173
LAN = '--lan' in sys.argv


def lan_ip():
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))  # no packet is sent; just picks the outgoing interface
        return s.getsockname()[0]
    except OSError:
        return '127.0.0.1'
    finally:
        s.close()


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, *args):
        pass


socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("0.0.0.0" if LAN else "127.0.0.1", PORT), Handler) as httpd:
    print(f"Pawcount running at http://localhost:{PORT}")
    if LAN:
        print(f"On your iPhone (same Wi-Fi), open Safari at: http://{lan_ip()}:{PORT}")
    httpd.serve_forever()
