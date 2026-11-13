"""음성 대본 범위·기존 녹음 호환성과 받아쓰기 정규화 검증."""

import hashlib
import json
import re
from pathlib import Path
import unittest
from unittest.mock import patch

from phrases import LANGS, cardinal, ja_number, phrases, sino, topic
from qa import normalize_heard, units

ROOT = Path(__file__).resolve().parents[2]


class PhraseTests(unittest.TestCase):
    def test_all_languages_cover_full_range(self):
        self.assertEqual(set(LANGS), {"ko", "en", "ja", "hi", "vi", "id"})
        expected = set()
        for a in range(2, 20):
            for b in range(1, 20):
                expected.update(f"{kind}-{a}x{b}" for kind in ("q", "m", "c"))
                for value in {a*b, a*(b+1), a*(b-1), (a+1)*b, (a-1)*b} - {0}:
                    expected.add(f"ox-{a}x{b}-{value}")
        for lang in LANGS:
            with self.subTest(lang=lang):
                self.assertEqual(set(phrases(lang)), expected)

    def test_existing_recorded_scripts_unchanged(self):
        # 확장 팩을 배치해도 기존 552문장의 원문 기준은 변경하지 않는다.
        original_hashes = {
            "ko": "b2a8b5f531cdd7c9ea1e472a4882efe16f0de9207d6732b23cf849cb5fa4109a",
            "en": "7141be39d2d56b990d9474d6623efb89ce2001d73740c09e964e03aaf65ee984",
            "ja": "703af78df55539113dfb402fe896030f3f8493bdda4dae740dd16c470bb867da",
        }
        for lang, original_hash in original_hashes.items():
            with self.subTest(lang=lang):
                path = ROOT / f"native-ios/Gugu/Resources/Voice/{lang}/voice-manifest.json"
                recorded = json.loads(path.read_text())["lines"]
                basic = re.compile(r"(q|m|c|ox)-[2-9]x[1-9](?:-[0-9]+)?")
                old = {key: text for key, text in recorded.items() if basic.fullmatch(key)}
                current = {key: text for key, text in phrases(lang).items() if basic.fullmatch(key)}
                self.assertEqual(len(old), 552)
                self.assertEqual(len(current), 552)
                self.assertEqual(current, old)
                canonical = json.dumps(old, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                self.assertEqual(hashlib.sha256(canonical.encode()).hexdigest(), original_hash)

    def test_expanded_readings_and_boundaries(self):
        self.assertEqual(cardinal(380), "three hundred eighty")
        self.assertEqual(cardinal(999), "nine hundred ninety-nine")
        self.assertEqual(ja_number(380), "さんびゃくはちじゅう")
        self.assertEqual(ja_number(600), "ろっぴゃく")
        self.assertEqual(ja_number(800), "はっぴゃく")
        self.assertEqual(ja_number(999), "きゅうひゃくきゅうじゅうきゅう")
        for read in (cardinal, ja_number):
            for value in (-1, 1000):
                with self.assertRaises(ValueError):
                    read(value)
        self.assertEqual(phrases("ja")["c-19x19"], "じゅうきゅう かける じゅうきゅう は さんびゃくろくじゅういち。")
        self.assertEqual(phrases("en")["ox-19x19-380"], "Nineteen times nineteen is three hundred eighty. True or false?")

    def test_expanded_korean_chants_separate_operands(self):
        lines = phrases("ko")
        changed = {}
        for a in range(2, 20):
            for b in range(1, 20):
                key = f"c-{a}x{b}"
                original = f"{sino(a)}{',' if b == 2 else ''} {topic(sino(b))} {sino(a * b)}."
                if a >= 10 or b >= 10:
                    self.assertEqual(lines[key], f"{sino(a)} 곱하기 {topic(sino(b))} {sino(a * b)}.")
                    self.assertNotEqual(lines[key], original)
                    changed[key] = lines[key]
                else:
                    self.assertEqual(lines[key], original)
        self.assertEqual(len(changed), 270)
        self.assertEqual(sum(map(len, changed.values())), 3912)

    def test_new_language_templates_match_app(self):
        expected = {
            "hi": ["19 गुणा 19 कितना होता है?", "19 को किस संख्या से गुणा करने पर 361 मिलता है?", "19 गुणा 19 बराबर 380। सही या गलत?", "19 गुणा 19 बराबर 361।"],
            "vi": ["19 nhân 19 bằng bao nhiêu?", "19 nhân bao nhiêu bằng 361?", "19 nhân 19 bằng 380. Đúng hay sai?", "19 nhân 19 bằng 361."],
            "id": ["Berapa 19 kali 19?", "19 dikali berapa hasilnya 361?", "19 kali 19 sama dengan 380. Benar atau salah?", "19 kali 19 sama dengan 361."],
        }
        for lang, lines in expected.items():
            with self.subTest(lang=lang):
                current = phrases(lang)
                self.assertEqual([current[key] for key in ("q-19x19", "m-19x19", "ox-19x19-380", "c-19x19")], lines)


class QATests(unittest.TestCase):
    def test_new_language_unicode_and_digits_preserved(self):
        for lang, source, expected in [
            ("hi", "19 गुणा 19 बराबर 361।", "19गुणा19बराबर361"),
            ("vi", "19 nhân 19 bằng 361.", "19nhân19bằng361"),
            ("id", "Berapa 19 kali 19?", "berapa19kali19"),
        ]:
            with self.subTest(lang=lang):
                self.assertEqual(units(source, lang), expected)
                self.assertEqual(normalize_heard(source, lang), expected)
        self.assertEqual(normalize_heard("mười chín", "vi"), "mườichín")

    def test_asr_expanded_numbers(self):
        self.assertEqual(normalize_heard("380", "en"), "threehundredeighty")
        with patch("qa.to_hiragana", side_effect=lambda text: text):
            self.assertEqual(normalize_heard("380", "ja"), "さんびゃくはちじゅう")


if __name__ == "__main__":
    unittest.main()
