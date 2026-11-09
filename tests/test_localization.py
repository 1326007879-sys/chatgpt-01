"""Owned UI strings remain complete in both supported languages."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"


def test_supported_languages_have_equal_resource_keys_and_format_parameters():
    def strings(folder):
        elements = ET.parse(RES / folder / "strings.xml").getroot().findall("string")
        assert len({e.attrib["name"] for e in elements}) == len(elements)
        return {e.attrib["name"]: e.text or "" for e in elements}
    chinese, english = strings("values"), strings("values-en")
    assert chinese.keys() == english.keys()
    for key in chinese:
        assert chinese[key].strip() and english[key].strip()
        assert re.findall(r"%\d+\$[.\d]*[sdf]", chinese[key]) == re.findall(
            r"%\d+\$[.\d]*[sdf]", english[key])


def test_all_controller_notices_have_localized_presentation_mapping():
    base = ROOT / "android/app/src/main/java/io/github/chatgpt01/petai"
    controller = (base / "chat/ui/ChatControllers.kt").read_text(encoding="utf-8")
    mapping = (base / "UiMessages.kt").read_text(encoding="utf-8")
    notices = re.findall(r'"([^"\n]*[\u4e00-\u9fff][^"\n]*)"', controller)
    for notice in notices:
        assert f'"{notice}" to R.string.' in mapping
