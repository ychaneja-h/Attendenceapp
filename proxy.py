import http.server
import urllib.request
import urllib.error

PORT = 8888
TARGET_HOST = "127.0.0.1"
TARGET_PORT = 8080

class ProxyHTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.handle_request("GET")

    def do_POST(self):
        self.handle_request("POST")

    def handle_request(self, method):
        # Forward to local Ktor server. We replace the host and port but keep the path.
        # Note: self.path usually contains the full URL in a proxy request (e.g. http://192.168.31.93:8080/api/...)
        # We need to extract just the path.
        from urllib.parse import urlparse
        parsed = urlparse(self.path)
        path = parsed.path
        if parsed.query:
            path += "?" + parsed.query

        url = f"http://{TARGET_HOST}:{TARGET_PORT}{path}"
        
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else None

        req_headers = {}
        for key, val in self.headers.items():
            if key.lower() not in ["host", "connection"]:
                req_headers[key] = val

        req = urllib.request.Request(url, data=body, headers=req_headers, method=method)

        try:
            with urllib.request.urlopen(req) as response:
                self.send_response(response.status)
                for key, val in response.getheaders():
                    self.send_header(key, val)
                self.end_headers()
                self.wfile.write(response.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for key, val in e.headers.items():
                self.send_header(key, val)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(str(e).encode())

if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = http.server.HTTPServer(server_address, ProxyHTTPRequestHandler)
    print(f"Proxy server running on port {PORT} and forwarding all requests to {TARGET_HOST}:{TARGET_PORT}")
    httpd.serve_forever()
