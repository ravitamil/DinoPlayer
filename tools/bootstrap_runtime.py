"""Fetch the pinned official Sherpa Android runtime; no speech models are downloaded."""
import hashlib
import os
from pathlib import Path
import urllib.request

URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
SIZE=50129134

def valid(path):
    if not path.is_file() or path.stat().st_size!=SIZE:return False
    digest=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1024*1024),b""):digest.update(block)
    return digest.hexdigest()==SHA256

def main():
    target=Path(__file__).resolve().parents[1]/"app/libs/sherpa-onnx-1.13.8.aar"
    target.parent.mkdir(parents=True,exist_ok=True)
    if valid(target):print("Sherpa-ONNX 1.13.8 already verified");return
    staging=target.with_suffix(".aar.download")
    try:
        print("Downloading official Sherpa-ONNX 1.13.8 runtime...")
        with urllib.request.urlopen(URL,timeout=120) as response,staging.open("wb") as output:
            total=0
            while True:
                block=response.read(1024*1024)
                if not block:break
                total+=len(block)
                if total>SIZE:raise RuntimeError("Runtime download exceeds its pinned size")
                output.write(block)
        if not valid(staging):raise RuntimeError("Runtime size or SHA-256 verification failed")
        os.replace(staging,target)
        print("Sherpa-ONNX 1.13.8 runtime verified")
    finally:
        if staging.exists():staging.unlink()

if __name__=="__main__":main()
