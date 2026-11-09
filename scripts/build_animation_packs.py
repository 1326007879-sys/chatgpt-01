"""Build one resource-only .petanim ZIP per non-core action from an exported catalog."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def build_packs(assets: Path, output: Path, version: int = 1) -> list[dict]:
    if version < 1:
        raise ValueError("version must be positive")
    catalog = json.loads((assets / "pet/manifest.json").read_text(encoding="utf-8"))
    if catalog["canvas"] != [640, 360] or catalog["body_box"] != [212, 60, 428, 330]:
        raise ValueError("unsupported character geometry")
    output.mkdir(parents=True, exist_ok=True)
    index = []
    seen_names = set()
    seen_ids = set()
    for clip in catalog["clips"]:
        if clip["group"] not in ("random", "events"):
            continue
        if (not clip["name"] or any(c in clip["name"] for c in "/\\") or
                clip["name"] in (".", "..") or clip["name"] in seen_names or
                not re.fullmatch(r"clips/[a-z]+_[a-f0-9]{12}", clip["path"]) or
                not 1 <= clip["frames"] <= 1800 or not math.isfinite(clip["fps"]) or
                not 1 <= clip["fps"] <= 60):
            raise ValueError("invalid or duplicate animation metadata")
        seen_names.add(clip["name"])
        identity = "ext_" + hashlib.sha256(clip["source"].encode("utf-8")).hexdigest()[:24]
        if identity in seen_ids:
            raise ValueError("duplicate source identity")
        seen_ids.add(identity)
        frames = [assets / "pet" / clip["path"] / f"f_{n:04d}.webp"
                  for n in range(1, clip["frames"] + 1)]
        if any(not p.resolve().is_relative_to(assets.resolve()) or p.stat().st_size > 2097152 for p in frames):
            raise ValueError("invalid frame path or size")
        if sum(p.stat().st_size for p in frames) > 255 * 1024 * 1024:
            raise ValueError("animation exceeds the import size limit")
        manifest = {"schema": 1, "format": "webp-frames-v1", "character": "shenshen",
                    "id": identity, "version": version, "name": clip["name"],
                    "nameEn": clip["name_en"], "canvas": catalog["canvas"],
                    "body": catalog["body_box"], "fps": clip["fps"], "frames": clip["frames"],
                    "crop": clip["crop_box"], "noMirror": clip.get("no_mirror", False),
                    "hashes": [hashlib.sha256(p.read_bytes()).hexdigest() for p in frames],
                    "source": "https://github.com/MerZlin/dsh-pet-indesktop"}
        target = output / (clip["name"] + ".petanim")
        temporary = target.with_suffix(".petanim.tmp")
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("pack.json", json.dumps(manifest, ensure_ascii=False))
            archive.write(ROOT / "android/NOTICE.txt", "NOTICE.txt")
            archive.write(ROOT / "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md")
            for n, path in enumerate(frames, 1):
                archive.write(path, f"frames/f_{n:04d}.webp")
        temporary.replace(target)
        with target.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        index.append({"id": identity, "name": clip["name"], "name_en": clip["name_en"],
                      "version": version, "file": target.name, "bytes": target.stat().st_size,
                      "sha256": digest})
    if not index:
        raise ValueError("No extension actions found. Export with --max-batch 3 first.")
    (output / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")
    return index


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", type=int, default=1)
    args = parser.parse_args()
    index = build_packs(args.assets.resolve(), args.output.resolve(), args.version)
    print(json.dumps({"packs": len(index), "bytes": sum(x["bytes"] for x in index)}))


if __name__ == "__main__":
    main()
