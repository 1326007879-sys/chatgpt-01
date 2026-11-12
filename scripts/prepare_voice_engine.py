"""Prepare the pinned Android runtime or data-only private research packs."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.request
import wave
import zipfile

RUNTIME_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar"
RUNTIME_SHA256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
MODEL = "zipvoice-distill-int8-v1"
ENGINE = "sherpa-zipvoice-v1"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def pack(output, files, *, kind, ident, name, reference_text, license_text):
    if output.exists():
        raise ValueError("Output already exists")
    if not ident or len(ident) > 64 or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in ident):
        raise ValueError("Invalid pack id")
    if ident[0] not in "abcdefghijklmnopqrstuvwxyz0123456789":
        raise ValueError("Invalid pack id")
    if not name.strip() or len(name) > 80 or not license_text.strip() or len(license_text) > 4000:
        raise ValueError("A name and license/source statement are required")
    if any(path.stat().st_size > 256 * 1024 * 1024 for path in files.values()) or sum(path.stat().st_size for path in files.values()) > 512 * 1024 * 1024:
        raise ValueError("Model pack exceeds Android import limits")
    header = dict(schema=1, kind=kind, engine=ENGINE, model=MODEL, id=ident, name=name,
                  referenceText=reference_text, license=license_text,
                  files={key: digest(path) for key, path in files.items()})
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("manifest.json", json.dumps(header, ensure_ascii=False))
            for key, path in files.items():
                archive.write(path, key)
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    runtime = sub.add_parser("runtime")
    runtime.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "android/voice-engine/libs/sherpa-onnx-2.0.1.aar")
    model = sub.add_parser("model")
    model.add_argument("--model-dir", type=Path, required=True)
    model.add_argument("--vocoder", type=Path, required=True)
    model.add_argument("--english-lexicon", type=Path, required=True)
    voice = sub.add_parser("voice")
    voice.add_argument("--reference", type=Path, required=True)
    voice.add_argument("--text", required=True)
    voice.add_argument("--id", required=True)
    voice.add_argument("--name", required=True)
    for command in [model, voice]:
        command.add_argument("--license", required=True, help="Source, rights and redistribution limitations")
        command.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "runtime":
        if args.output.exists() and digest(args.output) == RUNTIME_SHA256:
            print("Pinned runtime already verified")
            return
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_suffix(".download")
        try:
            with urllib.request.urlopen(RUNTIME_URL, timeout=120) as stream, temporary.open("wb") as target:
                for chunk in iter(lambda: stream.read(65536), b""):
                    target.write(chunk)
            if digest(temporary) != RUNTIME_SHA256:
                raise ValueError("Runtime checksum mismatch")
            os.replace(temporary, args.output)
        finally:
            temporary.unlink(missing_ok=True)
        return
    if args.command == "model":
        files = {name: args.model_dir / name for name in ["encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt", "lexicon.txt"]}
        files.update({"vocos_24khz.onnx": args.vocoder, "lexicon-en-us.txt": args.english_lexicon})
        pack(args.output, files, kind="model", ident=MODEL, name="Shared ZipVoice research model",
             reference_text="", license_text=args.license)
    else:
        with wave.open(str(args.reference), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) != (1, 2, 24000):
                raise ValueError("Reference must be mono 24kHz PCM16 WAV; no automatic pitch/audio conversion")
            if not 24000 <= audio.getnframes() <= 360000:
                raise ValueError("Reference must be 1–15 seconds")
        if not args.text.strip() or len(args.text) > 500:
            raise ValueError("Provide the accurate reference transcript")
        pack(args.output, {"reference.wav": args.reference}, kind="voice", ident=args.id,
             name=args.name, reference_text=args.text, license_text=args.license)


if __name__ == "__main__":
    main()
