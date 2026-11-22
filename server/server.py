#!/usr/bin/env python3
"""
CS4473 Mobile Computing - Mobile Network Measurement Server
Endpoints:
  - GET /status   : Server health check and metrics
  - GET /probe    : Tiny JSON payload for RTT latency measurement
  - GET /download : Exactly 1 MiB (1,048,576 bytes) payload for throughput measurement
  - POST /upload  : Optional endpoint to receive measurement CSV exports directly from phones
"""

import sys
import os
import time
import json
import socket
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
import ssl

ONE_MIB_BYTES = 1024 * 1024  # 1,048,576 bytes
# Pre-generate 1 MiB buffer in memory so disk I/O does not bottleneck network throughput measurements
CHUNK_PATTERN = b"CS4473_NET_TEST_" * 4096  # 65536 bytes
PAYLOAD_1MIB = (CHUNK_PATTERN * 16)[:ONE_MIB_BYTES]

server_stats = {
    "start_time": time.time(),
    "total_probes": 0,
    "total_downloads": 0,
    "bytes_sent": 0,
    "uploaded_files": 0
}


class NetworkMeasurementHandler(BaseHTTPRequestHandler):
    server_version = "CS4473NetworkServer/1.0"

    def log_message(self, format, *args):
        # Clean logging format with ISO timestamp
        sys.stderr.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {self.client_address[0]} - {format % args}\n")

    def _set_no_cache_headers(self, content_type="application/json", content_length=None):
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        if content_length is not None:
            self.send_header("Content-Length", str(content_length))

    def do_OPTIONS(self):
        self.send_response(204)
        self._set_no_cache_headers()
        self.end_headers()

    def do_GET(self):
        clean_path = self.path.split("?")[0].rstrip("/")
        if clean_path == "":
            clean_path = "/"

        if clean_path in ("/", "/status"):
            uptime = time.time() - server_stats["start_time"]
            status_data = {
                "server": "CS4473 Mobile Computing Measurement Server",
                "uptime_seconds": round(uptime, 2),
                "total_probes_served": server_stats["total_probes"],
                "total_downloads_served": server_stats["total_downloads"],
                "total_megabytes_sent": round(server_stats["bytes_sent"] / (1024 * 1024), 2),
                "endpoints": {
                    "/probe": "Tiny JSON response for latency measurement",
                    "/download": "Exact 1 MiB binary stream for download speed measurement",
                    "/upload": "POST CSV records directly from mobile app"
                }
            }
            body = json.dumps(status_data, indent=2).encode("utf-8")
            self.send_response(200)
            self._set_no_cache_headers("application/json", len(body))
            self.end_headers()
            self.wfile.write(body)

        elif clean_path == "/probe":
            server_stats["total_probes"] += 1
            probe_data = {
                "status": "ok",
                "timestamp_ns": time.time_ns(),
                "server_time": time.time()
            }
            body = json.dumps(probe_data).encode("utf-8")
            server_stats["bytes_sent"] += len(body)
            self.send_response(200)
            self._set_no_cache_headers("application/json", len(body))
            self.end_headers()
            self.wfile.write(body)

        elif clean_path == "/download":
            server_stats["total_downloads"] += 1
            server_stats["bytes_sent"] += ONE_MIB_BYTES
            self.send_response(200)
            self._set_no_cache_headers("application/octet-stream", ONE_MIB_BYTES)
            self.send_header("Content-Disposition", 'attachment; filename="1mib_probe.bin"')
            self.end_headers()
            
            # Stream in 64 KiB chunks to maintain smooth TCP window growth
            chunk_size = 65536
            for offset in range(0, ONE_MIB_BYTES, chunk_size):
                chunk = PAYLOAD_1MIB[offset:offset + chunk_size]
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    # Client disconnected or timeout occurred on phone
                    break

        else:
            self.send_response(404)
            self._set_no_cache_headers("text/plain")
            self.end_headers()
            self.wfile.write(b"404 Not Found. Available endpoints: /probe, /download, /status\n")

    def do_POST(self):
        clean_path = self.path.split("?")[0].rstrip("/")
        if clean_path == "/upload":
            try:
                content_length = int(self.headers.get("Content-Length", 0))
                if content_length > 10 * 1024 * 1024:  # 10 MB limit
                    self.send_response(413)
                    self.end_headers()
                    self.wfile.write(b"Payload too large\n")
                    return

                post_data = self.rfile.read(content_length)
                timestamp_str = time.strftime("%Y%m%d_%H%M%S")
                os.makedirs("uploads", exist_ok=True)
                filename = os.path.join("uploads", f"measurements_{timestamp_str}.csv")
                with open(filename, "wb") as f:
                    f.write(post_data)

                server_stats["uploaded_files"] += 1
                resp = json.dumps({"status": "success", "saved_as": filename, "bytes": content_length}).encode("utf-8")
                self.send_response(200)
                self._set_no_cache_headers("application/json", len(resp))
                self.end_headers()
                self.wfile.write(resp)
            except Exception as e:
                self.send_response(500)
                self._set_no_cache_headers("application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()


def get_local_ip_addresses():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Doesn't need to actually connect, just routing resolution
        s.connect(("8.8.8.8", 80))
        ips.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    return ips


def main():
    parser = argparse.ArgumentParser(description="CS4473 Mobile Network Measurement Server")
    parser.add_argument("--host", default="0.0.0.0", help="Host interface to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Port to listen on (default: 8000)")
    parser.add_argument("--ssl", action="store_true", help="Enable HTTPS using SSL certificate")
    parser.add_argument("--cert", default="cert.pem", help="SSL certificate path (for HTTPS)")
    parser.add_argument("--key", default="key.pem", help="SSL private key path (for HTTPS)")
    args = parser.parse_args()

    httpd = HTTPServer((args.host, args.port), NetworkMeasurementHandler)

    protocol = "http"
    if args.ssl:
        if not os.path.exists(args.cert) or not os.path.exists(args.key):
            print(f"Error: SSL cert '{args.cert}' or key '{args.key}' not found.")
            print("Run 'python3 generate_certs.py' first, or run without --ssl (e.g. behind Cloudflare Tunnel).")
            sys.exit(1)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile=args.cert, keyfile=args.key)
        httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
        protocol = "https"

    local_ips = get_local_ip_addresses()
    primary_ip = local_ips[0] if local_ips else "127.0.0.1"

    print("=" * 65)
    print("  CS4473 Mobile Network Measurement Server")
    print("=" * 65)
    print(f"Listening on       : {protocol}://{args.host}:{args.port}")
    print(f"Local LAN URL      : {protocol}://{primary_ip}:{args.port}")
    print("Available Endpoints:")
    print(f"  • Status / Health : {protocol}://{primary_ip}:{args.port}/status")
    print(f"  • RTT Probe       : {protocol}://{primary_ip}:{args.port}/probe")
    print(f"  • 1 MiB Download  : {protocol}://{primary_ip}:{args.port}/download")
    print(f"  • CSV Upload      : {protocol}://{primary_ip}:{args.port}/upload")
    print("-" * 65)
    print("Public HTTPS access for both Wi-Fi and Cellular testing:")
    print("  Run Cloudflare Tunnel in another terminal:")
    print(f"    cloudflared tunnel --url http://127.0.0.1:{args.port}")
    print("  or ngrok:")
    print(f"    ngrok http {args.port}")
    print("=" * 65)
    print("Server ready. Press Ctrl+C to terminate.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down measurement server...")
        httpd.server_close()
        sys.exit(0)


if __name__ == "__main__":
    main()
