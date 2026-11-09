import hashlib
import json
import zipfile

from PIL import Image

from scripts.build_animation_packs import build_packs


def test_single_action_pack_preserves_pixels_metadata_and_notices(tmp_path):
    assets = tmp_path / "assets"
    frames = assets / "pet/clips/random_0123456789ab"
    frames.mkdir(parents=True)
    frame = frames / "f_0001.webp"
    Image.new("RGBA", (640, 360), (20, 50, 90, 128)).save(frame, lossless=True)
    clip = {"group": "random", "path": "clips/random_0123456789ab", "source": "random/睡觉.webm",
            "name": "睡觉", "name_en": "Nap", "frames": 1, "fps": 24,
            "crop_box": [0, 0, 640, 360], "no_mirror": True}
    (assets / "pet/manifest.json").write_text(json.dumps({"canvas": [640, 360],
        "body_box": [212, 60, 428, 330], "clips": [clip, {"group": "idle"}]}))
    index = build_packs(assets, tmp_path / "packs")
    assert len(index) == 1
    path = tmp_path / "packs" / index[0]["file"]
    assert hashlib.sha256(path.read_bytes()).hexdigest() == index[0]["sha256"]
    with zipfile.ZipFile(path) as z:
        m = json.loads(z.read("pack.json"))
        assert m["name"] == "睡觉" and m["noMirror"]
        assert m["hashes"] == [hashlib.sha256(frame.read_bytes()).hexdigest()]
        assert z.read("frames/f_0001.webp") == frame.read_bytes()
        assert {"NOTICE.txt", "THIRD_PARTY_NOTICES.md"} <= set(z.namelist())
