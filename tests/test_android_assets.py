"""Public build-export contract; no Android SDK or network required."""
import json

import pytest
from PIL import Image

from scripts.export_android_assets import copy_click_sounds, export_prepared


def prepared(tmp_path):
    video = tmp_path / "videos"
    video.mkdir()
    (video / "manifest.json").write_text(json.dumps({"body_box": [2, 1, 6, 7]}))
    clips = {}
    for group in ("idle", "move", "turn", "click", "drag"):
        source = video / group / "pet.webm"
        source.parent.mkdir()
        source.write_bytes(b"source")
        frames = tmp_path / "frames" / group
        frames.mkdir(parents=True)
        (frames / "meta.json").write_text(json.dumps({"fps": 24, "frames": 2}))
        for n in (1, 2):
            im = Image.new("RGBA", (8, 8))
            im.paste((25, 120, 180, 255), (2, 1, 6, 7))
            im.save(frames / f"f_{n:04d}.webp", lossless=True)
        clips[source] = frames
    return video, clips


def test_export_preserves_alpha_timing_geometry_and_source_identity(tmp_path):
    video, clips = prepared(tmp_path)
    output = tmp_path / "mobile"
    result = export_prepared(video, clips, output, ["Hello"])
    assert result["schema_version"] == 1
    assert result["body_box"] == [2, 1, 6, 7]
    assert result["phrases"] == ["Hello"]
    assert {c["group"] for c in result["clips"]} == {"idle", "move", "turn", "click", "drag"}
    for clip in result["clips"]:
        assert clip["fps"] == 24
        assert clip["frames"] == 2
        assert clip["source"].endswith("pet.webm")
        frame = Image.open(output / "pet" / clip["path"] / "f_0001.webp")
        assert frame.mode == "RGBA"
        assert frame.getpixel((0, 0))[3] == 0
    assert json.loads((output / "pet/manifest.json").read_text(encoding="utf8")) == result


def test_missing_required_action_fails_build(tmp_path):
    video, clips = prepared(tmp_path)
    clips = {s: f for s, f in clips.items() if s.parent.name != "drag"}
    with pytest.raises(ValueError, match="drag"):
        export_prepared(video, clips, tmp_path / "mobile", ["Hello"])


def test_missing_frame_fails_before_publishing_manifest(tmp_path):
    video, clips = prepared(tmp_path)
    next(iter(clips.values())).joinpath("f_0002.webp").unlink()
    output = tmp_path / "mobile"
    with pytest.raises(ValueError, match="frame"):
        export_prepared(video, clips, output, ["Hello"])
    assert not (output / "pet/manifest.json").exists()


def test_export_cannot_write_inside_source_package(tmp_path):
    video, clips = prepared(tmp_path)
    with pytest.raises(ValueError, match="source"):
        export_prepared(video, clips, video / "mobile", ["Hello"])


def test_click_sounds_land_as_raw_resources(tmp_path):
    sounds = tmp_path / "duck"
    sounds.mkdir()
    (sounds / "Ya1.mp3").write_bytes(b"press bytes")
    (sounds / "Ya2.mp3").write_bytes(b"release bytes")
    res = tmp_path / "res"
    written = copy_click_sounds(sounds, res)
    assert [p.name for p in written] == ["click_press.mp3", "click_release.mp3"]
    assert [p.relative_to(res).as_posix() for p in written] == [
        "raw/click_press.mp3", "raw/click_release.mp3"]
    assert (res / "raw/click_press.mp3").read_bytes() == b"press bytes"
    assert (res / "raw/click_release.mp3").read_bytes() == b"release bytes"


def test_missing_click_sound_fails_build(tmp_path):
    sounds = tmp_path / "duck"
    sounds.mkdir()
    (sounds / "Ya1.mp3").write_bytes(b"press bytes")
    with pytest.raises(FileNotFoundError, match="Ya2.mp3"):
        copy_click_sounds(sounds, tmp_path / "res")


def test_random_and_nested_event_animations_export_and_return_to_same_geometry(tmp_path):
    video, clips = prepared(tmp_path)
    template = next(iter(clips.values()))
    for name in ("random/nap.webm", "events/balance/empty.webm"):
        source = video / name
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_bytes(b"animation")
        clips[source] = template
    result = export_prepared(video, clips, tmp_path / "mobile", [])
    assert len(result["clips"]) == 7
    extra = {c["source"]: c for c in result["clips"]}
    assert extra["random/nap.webm"]["group"] == "random"
    assert extra["events/balance/empty.webm"]["group"] == "events"
    assert len({c["id"] for c in result["clips"]}) == 7
    for c in result["clips"]:
        assert c["crop_box"] == [0, 0, 8, 8]


def test_bundled_catalog_matches_upstream_source_hashes_and_all_three_batches():
    import hashlib
    from collections import Counter
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    catalog = json.loads((root / "tools/asset_pipeline/animation_catalog.json").read_text(encoding="utf-8"))
    assert catalog["commit"] == "34d810b1eadd98d00e7cb9b3b51428a45b5bf03e"
    entries = catalog["clips"]
    assert Counter(c["batch"] for c in entries) == {0: 11, 1: 12, 2: 77, 3: 6}
    assert len({c["source"] for c in entries}) == 106
    for c in entries:
        path = root / "assets/characters/shenshen/videos" / c["source"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == c["sha256"]
        assert c["name_en"].strip()


def test_switching_to_smaller_batch_removes_unselected_generated_frames(tmp_path):
    video, clips = prepared(tmp_path)
    output = tmp_path / "mobile"
    extra = video / "random" / "nap.webm"
    extra.parent.mkdir()
    extra.write_bytes(b"extra")
    expanded = export_prepared(video, {**clips, extra: next(iter(clips.values()))}, output, [])
    cold = next(c for c in expanded["clips"] if c["group"] == "random")
    cold_dir = output / "pet" / cold["path"]
    assert cold_dir.is_dir()
    smaller = export_prepared(video, clips, output, [])
    assert len(smaller["clips"]) == 5
    assert not cold_dir.exists()
    assert len(list((output / "pet/clips").iterdir())) == 5


def test_text_animation_keeps_its_no_mirror_flag(tmp_path):
    video, clips = prepared(tmp_path)
    (video / "text_clips.json").write_text(json.dumps({"no_mirror": ["pet"]}))
    result = export_prepared(video, clips, tmp_path / "mobile", [])
    assert all(c["no_mirror"] for c in result["clips"])
