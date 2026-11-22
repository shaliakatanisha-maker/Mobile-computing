#!/usr/bin/env python3
"""
Generates a self-signed SSL certificate for local HTTPS testing if needed.
Usage: python3 generate_certs.py
"""
import subprocess
import sys
import os

def generate_self_signed_cert():
    cert_file = "cert.pem"
    key_file = "key.pem"
    
    if os.path.exists(cert_file) and os.path.exists(key_file):
        print(f"Certificates '{cert_file}' and '{key_file}' already exist.")
        return

    print("Generating self-signed SSL certificate for testing...")
    cmd = [
        "openssl", "req", "-x509", "-newkey", "rsa:2048",
        "-keyout", key_file,
        "-out", cert_file,
        "-days", "365",
        "-nodes",
        "-subj", "/CN=cs4473.mobilecomputing.test/O=University/OU=CS4473"
    ]
    try:
        subprocess.run(cmd, check=True)
        print(f"Successfully generated:\n  - Certificate : {cert_file}\n  - Private Key : {key_file}")
        print("\nNote: When running on a public tunnel (e.g. Cloudflare Tunnel or ngrok),")
        print("HTTPS certificates are handled automatically by the tunnel provider.")
    except Exception as e:
        print(f"Error running openssl: {e}")
        print("Please ensure OpenSSL is installed, or use Cloudflare Tunnel.")

if __name__ == "__main__":
    generate_self_signed_cert()
