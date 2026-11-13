"""Verify and stage preview.6 host-only release assets; never publish anything."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TAG = "v0.1.0-preview.6"
REPOSITORY = "1326007879-sys/chatgpt-01"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    assert source == os.environ["SOURCE_SHA"]
    assert os.environ["GH_REPO"] == REPOSITORY
    assert not subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT, text=True).strip()
    output = ROOT / "dist/preview6-publication"
    output.mkdir(parents=True, exist_ok=False)
    original = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    name = "chatgpt01-petai-0.1.0-preview.6-debug.apk"
    target = output / name
    shutil.copyfile(original, target)
    signatures = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(target)], text=True)
    metadata = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(target)], text=True)
    assert "name='io.github.chatgpt01.petai.preview6'" in metadata
    assert "versionCode='6'" in metadata and "versionName='0.1.0-preview.6'" in metadata
    assert "sdkVersion:'26'" in metadata and "targetSdkVersion:'36'" in metadata
    with zipfile.ZipFile(target) as archive:
        catalog = json.loads(archive.read("assets/pet/manifest.json"))
        assert len(catalog["clips"]) == 11
        assert sum(c["frames"] for c in catalog["clips"]) == 2651
        native = [p for p in archive.namelist() if p.startswith("lib/") and not p.endswith("/")]
        assert native and all(Path(p).name == "libandroidx.graphics.path.so" for p in native)
        assert not any(p.endswith((".onnx", ".petmodel", ".petvoice")) for p in archive.namelist())
    (output / "SIGNATURES.txt").write_text(name + "\n" + signatures)
    (output / "APK-METADATA.txt").write_text(name + "\n" + metadata)
    counts = {}
    for module, expected in [("app", 190), ("plugin-notes", 3), ("voice-engine", 4)]:
        reports = [ET.parse(p).getroot() for p in (ROOT / "android" / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
        count = sum(int(r.get("tests", "0")) for r in reports)
        assert count == expected and not sum(int(r.get("failures", "0")) + int(r.get("errors", "0")) + int(r.get("skipped", "0")) for r in reports)
        counts[module] = count
    for document in ["INSTALL.md", "KNOWN_ISSUES.md", "RELEASE_NOTES.md"]:
        shutil.copyfile(ROOT / "docs/releases" / TAG / document, output / document)
    for document in ["LICENSE", "THIRD_PARTY_NOTICES.md"]:
        shutil.copyfile(ROOT / document, output / document)
    record = {
        "repository": REPOSITORY, "commit": source, "tag": TAG,
        "apks": [{"file": name, "package": "io.github.chatgpt01.petai.preview6", "bytes": target.stat().st_size, "sha256": digest(target)}],
        "checks": {"jvm": counts, "python": 13, "ruff": "passed", "lint": "passed", "instrumentation_apk": "built, not device accepted"},
        "device_acceptance": "Final preview6 APK not device accepted; original reminder issue not reproduced or fixed",
        "signing": "Local Android Debug certificate; separate preview6 identity, no overwrite of preview5",
        "not_included": ["offline voice engine APK", "speech model weights", "voice packs", "private reference audio", "instrumentation APKs", "keys"],
        "plugins": "Reuse preview5/preview4 published plugin binaries; do not replace existing note data",
        "animation_packs": "Reuse compatible preview3 petanim assets",
    }
    (output / "SOURCE.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
    expected = {p.name: {"bytes": p.stat().st_size, "sha256": digest(p)} for p in sorted(output.iterdir())}
    (output / "SHA256SUMS.txt").write_text("".join(v["sha256"] + "  " + k + "\n" for k, v in expected.items()))
    sums = output / "SHA256SUMS.txt"
    expected[sums.name] = {"bytes": sums.stat().st_size, "sha256": digest(sums)}
    assert len(expected) == 10
    (ROOT / "dist/preview6-expected.json").write_text(json.dumps(expected, indent=2) + "\n")
    print(json.dumps({"assets": len(expected), "apk": name, "source": source}))


if __name__ == "__main__":
    main()
