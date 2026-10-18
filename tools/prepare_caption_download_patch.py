"""Create the small, pinned streaming transform used by the English pack installer.

Run after prepare_whisper_alignment.py. No weights are quantized or trained here.
The reconstructed bytes must exactly match the tested attention-output decoder.
"""
import argparse
import hashlib
import json
from pathlib import Path


def prepare(original: Path, aligned: Path, destination: Path):
    source, target = original.read_bytes(), aligned.read_bytes()
    start = 1024 * 1024
    anchor = source[start:start + 4096]
    position = target.find(anchor)
    if len(anchor) != 4096 or position < 0:
        raise ValueError("Expected the pinned tiny.en decoder pair")
    source_start, target_start = start, position
    while source_start and target_start and source[source_start - 1] == target[target_start - 1]:
        source_start -= 1
        target_start -= 1
    source_end, target_end = start + 4096, position + 4096
    while (source_end + 65536 <= len(source) and target_end + 65536 <= len(target)
           and source[source_end:source_end + 65536] == target[target_end:target_end + 65536]):
        source_end += 65536
        target_end += 65536
    while source_end < len(source) and target_end < len(target) and source[source_end] == target[target_end]:
        source_end += 1
        target_end += 1
    head, tail = target[:target_start], target[target_end:]
    if head + source[source_start:source_end] + tail != target:
        raise ValueError("The models need a different transform; refusing an inexact patch")
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "english-decoder-head.bin").write_bytes(head)
    (destination / "english-decoder-tail.bin").write_bytes(tail)
    manifest = {
        "source_sha256": hashlib.sha256(source).hexdigest(),
        "target_sha256": hashlib.sha256(target).hexdigest(),
        "source_start": source_start, "source_end": source_end,
        "target_size": len(target), "head_bytes": len(head), "tail_bytes": len(tail),
    }
    (destination / "english-decoder.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("aligned", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    prepare(args.original, args.aligned, args.destination)
