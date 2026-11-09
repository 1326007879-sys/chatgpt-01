"""Export staged character animations as an offline Android asset package.

The desktop converter remains the only WebM/alpha decoder. This exporter copies
its prepared WebP frames and derives the mobile catalog, never edits source art.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ProcessPoolExecutor
import multiprocessing
import os
import hashlib
import json
import math
import shutil
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BUNDLED_GROUPS = frozenset(("idle", "move", "turn", "click", "drag", "random", "events"))
CORE_GROUPS = frozenset(("idle", "move", "turn", "click", "drag"))
CLICK_SOUND_SOURCES = {"click_press.mp3": "Ya1.mp3", "click_release.mp3": "Ya2.mp3"}
#: Assets shipped with the APK as license/notice files. The click sounds below are
#: MIT assets (MeteorNOX/DeepSeek-Balance-Whale-Widget) whose full license text lives
#: in the repository's THIRD_PARTY_NOTICES.md - a pointer to the source repository is
#: not a copy of the material, so that file travels with the APK assets too.
NOTICE_FILES = (
    (ROOT / "android" / "NOTICE.txt", "NOTICE.txt"),
    (ROOT / "android" / "LICENSE-APACHE-2.0.txt", "LICENSE-APACHE-2.0.txt"),
    (ROOT / "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md"),
)


def copy_notices(output: Path) -> list[Path]:
    """Copy the license/notice files into the APK asset root (missing one is fatal)."""
    written = []
    for source, name in NOTICE_FILES:
        if not source.is_file():
            raise FileNotFoundError(f"missing notice file: {source}")
        destination = output / name
        shutil.copyfile(source, destination)
        written.append(destination)
    return written


def copy_click_sounds(sounds_root: Path, res_output: Path) -> list[Path]:
    target = res_output / "raw"
    target.mkdir(parents=True, exist_ok=True)
    written = []
    for name, origin in sorted(CLICK_SOUND_SOURCES.items()):
        source = sounds_root / origin
        if not source.is_file():
            raise FileNotFoundError(f"missing click sound: {source}")
        destination = target / name
        shutil.copyfile(source, destination)
        written.append(destination)
    return written


def export_prepared(videos: Path, prepared: dict[Path, Path], output: Path,
                    phrases: list[str]) -> dict:
    videos, output = videos.resolve(), output.resolve()
    if output == videos or videos in output.parents or output in videos.parents:
        raise ValueError("output overlaps the source package")
    groups = {source.resolve().relative_to(videos).parts[0] for source in prepared}
    if not CORE_GROUPS <= groups:
        raise ValueError(f"missing required actions: {sorted(CORE_GROUPS - groups)}")
    geometry = json.loads((videos / "manifest.json").read_text(encoding="utf-8"))
    text_file = videos / "text_clips.json"
    no_mirror = set(json.loads(text_file.read_text(encoding="utf-8")).get("no_mirror", [])) if text_file.exists() else set()
    strides_file = videos / "move_strides.json"
    strides = json.loads(strides_file.read_text(encoding="utf-8")) if strides_file.exists() else {}
    catalog = json.loads((ROOT / "tools/asset_pipeline/animation_catalog.json").read_text(encoding="utf-8"))
    labels = {c["source"]: c for c in catalog["clips"]}
    clips = []
    canvas = None
    for source, directory in sorted(prepared.items(), key=lambda pair: pair[0].as_posix()):
        relative = source.resolve().relative_to(videos).as_posix()
        group = relative.split("/")[0]
        if group not in BUNDLED_GROUPS:
            continue
        meta = json.loads((directory / "meta.json").read_text(encoding="utf-8"))
        count, fps = int(meta["frames"]), float(meta["fps"])
        if count < 1 or not math.isfinite(fps) or fps <= 0:
            raise ValueError(f"invalid frame metadata: {relative}")
        bounds = None
        for index in range(1, count + 1):
            frame = directory / f"f_{index:04d}.webp"
            if not frame.is_file():
                raise ValueError(f"missing frame: {frame}")
            with Image.open(frame) as image:
                if canvas is None:
                    canvas = image.size
                if image.size != canvas or "A" not in image.getbands():
                    raise ValueError(f"invalid frame geometry/alpha: {frame}")
                bbox = image.getchannel("A").point(lambda a: 255 if a >= 16 else 0).getbbox()
                if bbox:
                    bounds = bbox if bounds is None else (
                        min(bounds[0], bbox[0]), min(bounds[1], bbox[1]),
                        max(bounds[2], bbox[2]), max(bounds[3], bbox[3]))
        if bounds is None:
            raise ValueError(f"empty animation: {relative}")
        # Symmetric crop keeps mirroring and the desktop body anchor aligned.
        left = max(0, min(bounds[0], canvas[0] - bounds[2]) - 2)
        crop = [left, max(0, bounds[1] - 2), canvas[0] - left, min(canvas[1], bounds[3] + 2)]
        identity = group + "_" + hashlib.sha256(relative.encode("utf-8")).hexdigest()[:12]
        movement = strides.get(source.stem, {})
        if isinstance(movement, (int, float)):
            movement = {"stride": movement}
        clips.append({"id": identity, "group": group, "source": relative,
                      "name": source.stem, "name_en": labels.get(relative, {}).get("name_en", source.stem),
                      "batch": labels.get(relative, {}).get("batch", 0), "no_mirror": source.stem in no_mirror,
                      "fps": fps, "frames": count,
                      "path": f"clips/{identity}", "crop_box": crop,
                      "stride": float(movement.get("stride", 180)),
                      "curve": movement.get("curve", []),
                      "_directory": directory})
    if canvas is None:
        raise ValueError("no frames")
    body = geometry["body_box"]
    if not (len(body) == 4 and 0 <= body[0] < body[2] <= canvas[0]
            and 0 <= body[1] < body[3] <= canvas[1]):
        raise ValueError("invalid body_box")
    pet_dir = output / "pet"
    pet_dir.mkdir(parents=True, exist_ok=True)
    expected = {clip["id"] for clip in clips}
    clips_root = pet_dir / "clips"
    if clips_root.is_dir():
        for old in clips_root.iterdir():
            if old.is_dir() and old.name not in expected:
                shutil.rmtree(old)
    for clip in clips:
        directory = clip.pop("_directory")
        target = pet_dir / clip["path"]
        target.mkdir(parents=True, exist_ok=True)
        for index in range(1, clip["frames"] + 1):
            name = f"f_{index:04d}.webp"
            shutil.copyfile(directory / name, target / name)
    manifest = {"schema_version": 1, "character_id": videos.parent.name,
                "canvas": list(canvas), "body_box": body, "clips": clips,
                "phrases": list(phrases)}
    temporary = pet_dir / "manifest.json.tmp"
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(pet_dir / "manifest.json")
    return manifest


def prepare_source(job: tuple[Path, Path]) -> tuple[Path, Path]:
    """Each worker owns a distinct source/cache directory; never share a clip writer."""
    from tools.asset_pipeline.frameseq import clip_out_dir, convert_clip

    source, videos = job
    directory = clip_out_dir(source, videos, videos.parent / "frameseq")
    _, error = convert_clip(source, directory)
    if error:
        raise RuntimeError(f"{source.name}: {error}")
    return source, directory


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--res-output", type=Path)
    parser.add_argument("--jobs", type=int, choices=range(1, 5), default=min(3, os.cpu_count() or 1))
    parser.add_argument("--max-batch", type=int, choices=range(4), default=0,
                        help="0=core; 1=common; 2=all random; 3=all animations")
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT))
    DEFAULT_SELF_TALK_TEXTS = json.loads((ROOT / "tools/asset_pipeline/default_phrases.json").read_text(encoding="utf-8"))
    from tools.asset_pipeline.frameseq import hot_webms
    from tools.asset_pipeline.icons import prepare_icon_image

    started = time.perf_counter()
    videos = ROOT / "assets/characters/shenshen/videos"
    sources = []
    catalog = json.loads((ROOT / "tools/asset_pipeline/animation_catalog.json").read_text(encoding="utf-8"))
    entries = {c["source"]: c for c in catalog["clips"]}
    expected = {name for name, record in entries.items() if record["batch"] <= args.max_batch}
    available = hot_webms(videos, BUNDLED_GROUPS)
    present = {source.relative_to(videos).as_posix() for source in available}
    if missing := expected - present:
        raise ValueError(f"missing catalog sources: {sorted(missing)}")
    for source in available:
        relative = source.relative_to(videos).as_posix()
        record = entries[relative]
        if record["batch"] > args.max_batch:
            continue
        if hashlib.sha256(source.read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError(f"source identity mismatch: {relative}")
        sources.append(source)
    with ProcessPoolExecutor(max_workers=args.jobs, mp_context=multiprocessing.get_context("spawn")) as pool:
        prepared = dict(pool.map(prepare_source, ((source, videos) for source in sources)))
    manifest = export_prepared(videos, prepared, args.output, DEFAULT_SELF_TALK_TEXTS)
    copy_notices(args.output)
    if args.res_output:
        idle = next(c for c in manifest["clips"] if c["group"] == "idle")
        first = args.output / "pet" / idle["path"] / "f_0001.webp"
        with Image.open(first) as source:
            icon = prepare_icon_image(source, side=192)
        resource = args.res_output / "drawable-nodpi"
        resource.mkdir(parents=True, exist_ok=True)
        icon.save(resource / "pet_icon.png")
        copy_click_sounds(ROOT / "assets" / "sounds" / "duck", args.res_output)
    byte_count = sum(p.stat().st_size for p in args.output.rglob("*") if p.is_file())
    print(json.dumps({"clips": len(manifest["clips"]),
                      "frames": sum(c["frames"] for c in manifest["clips"]),
                      "bytes": byte_count, "seconds": round(time.perf_counter() - started, 3)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
