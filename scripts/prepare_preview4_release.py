"""Prepare and verify the authorized preview.4 release, without publishing it."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    assert source == os.environ["SOURCE_SHA"]
    assert os.environ["GH_REPO"] == "1326007879-sys/chatgpt-01"
    output = ROOT / "dist/preview4-publication"
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    specs = [
        ("app", "chatgpt01-petai-0.1.0-preview.4-debug.apk", "io.github.chatgpt01.petai.preview4"),
        ("plugin-notes", "petai-notes-plugin-0.1.0-test-debug.apk", "io.github.chatgpt01.plugins.notes"),
        ("plugin-fault", "petai-fault-plugin-0.1.0-test-debug.apk", "io.github.chatgpt01.plugins.fault"),
    ]
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    apks = []
    for module, name, pkg in specs:
        original = next((ROOT / "android" / module / "build/outputs/apk/debug").glob("*.apk"))
        target = output / name
        shutil.copyfile(original, target)
        signatures = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(target)], text=True)
        metadata = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(target)], text=True)
        assert f"name='{pkg}'" in metadata
        with (output / "SIGNATURES.txt").open("a") as f:
            f.write(name + "\n" + signatures + "\n")
        with (output / "APK-METADATA.txt").open("a") as f:
            f.write(name + "\n" + metadata + "\n")
        if module == "app":
            assert "versionCode='4'" in metadata and "versionName='0.1.0-preview.4'" in metadata
            with zipfile.ZipFile(target) as archive:
                catalog = json.loads(archive.read("assets/pet/manifest.json"))
                assert len(catalog["clips"]) == 11
                assert sum(c["frames"] for c in catalog["clips"]) == 2651
        else:
            assert "versionCode='1'" in metadata
            assert "uses-permission:" not in metadata
        apks.append({"file": name, "package": pkg, "bytes": target.stat().st_size, "sha256": digest(target)})
    counts = {}
    for module, expected in [("app", 166), ("plugin-notes", 3)]:
        reports = [ET.parse(p).getroot() for p in (ROOT / "android" / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
        count = sum(int(r.get("tests", "0")) for r in reports)
        assert count == expected and not sum(int(r.get("failures", "0")) + int(r.get("errors", "0")) for r in reports)
        counts[module] = count
    for name in ["INSTALL.md", "KNOWN_ISSUES.md"]:
        shutil.copyfile(ROOT / "docs/releases/v0.1.0-preview.4" / name, output / name)
    for src, name in [("LICENSE", "LICENSE"), ("THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md"), ("docs/PLUGIN_PROTOCOL.md", "PLUGIN_PROTOCOL.md")]:
        shutil.copyfile(ROOT / src, output / name)
    record = {"repository": os.environ["GH_REPO"], "commit": source, "tag": "v0.1.0-preview.4",
              "publisher_commit": os.environ.get("GITHUB_SHA"), "apks": apks,
              "checks": {"jvm": counts, "python": 13, "lint": "passed", "instrumentation_apk": "built"},
              "device_acceptance": "NOT PASSED: authorization UI timeouts and host startup ANR; IPC tests did not complete",
              "signing": "GitHub Actions ephemeral Debug signing; independent preview4 host identity",
              "animation_packs": "reuse compatible preview.3 .petanim files",
              "run_url": "https://github.com/1326007879-sys/chatgpt-01/actions/runs/" + os.environ.get("GITHUB_RUN_ID", "local")}
    (output / "SOURCE.json").write_text(json.dumps(record, indent=2) + "\n")
    expected = {p.name: {"bytes": p.stat().st_size, "sha256": digest(p)} for p in sorted(output.iterdir())}
    (output / "SHA256SUMS.txt").write_text("".join(v["sha256"] + "  " + k + "\n" for k, v in expected.items()))
    sums = output / "SHA256SUMS.txt"
    expected[sums.name] = {"bytes": sums.stat().st_size, "sha256": digest(sums)}
    assert len(expected) == 12
    (ROOT / "dist/preview4-expected.json").write_text(json.dumps(expected, indent=2))
    print(json.dumps({"assets": len(expected), "apks": apks}))


if __name__ == "__main__":
    main()
