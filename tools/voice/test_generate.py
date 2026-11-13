"""음성 팩의 재사용·비용 계산·배치 실패 검증."""

import argparse
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import generate


class BuildTests(unittest.TestCase):
    def setUp(self):
        network = patch.object(generate.requests.Session, "request", side_effect=AssertionError("API 호출 금지"))
        network.start()
        self.addCleanup(network.stop)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        for name, value in {"CACHE": root / "cache", "IOS_VOICE": root / "ios",
                            "ANDROID_VOICE": root / "android"}.items():
            patcher = patch.object(generate, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.lines = {"q-2x1": "question", "c-2x1": "answer"}
        self.args = argparse.Namespace(lang="ko", voice="voice", model=generate.DEFAULT_MODEL,
                                       regen=[], workers=1, dry_run=False)
        for base, filename in [(generate.IOS_VOICE, generate.IOS_MANIFEST),
                               (generate.ANDROID_VOICE, generate.ANDROID_MANIFEST)]:
            out = base / "ko"
            out.mkdir(parents=True)
            for key in self.lines:
                (out / f"{key}.m4a").write_bytes(b"original")
            (out / filename).write_text(json.dumps({"voice": "voice", "model": self.args.model,
                "settings": generate.settings_for(self.args.model), "lines": self.lines}))
        patcher = patch.object(generate, "phrases", return_value=self.lines)
        patcher.start()
        self.addCleanup(patcher.stop)

    def build(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            generate.cmd_build(self.args)
        return output.getvalue()

    def test_dry_run_has_no_api_or_writes(self):
        self.args.dry_run = True
        with patch.object(generate, "session") as session, patch.object(generate, "master") as master:
            output = self.build()
        session.assert_not_called()
        master.assert_not_called()
        self.assertFalse(generate.CACHE.exists())
        self.assertIn("기존 M4A 재사용 2", output)
        self.assertIn("새로 생성 0 (0자)", output)

    def test_existing_m4a_without_pcm_reused_and_synced(self):
        (generate.ANDROID_VOICE / "ko/q-2x1.m4a").write_bytes(b"old android")
        with patch.object(generate, "session") as session, patch.object(generate, "master") as master:
            self.build()
        session.assert_not_called()
        master.assert_not_called()
        self.assertEqual((generate.ANDROID_VOICE / "ko/q-2x1.m4a").read_bytes(), b"original")

    def test_changed_text_and_regen_require_generation(self):
        self.lines["q-2x1"] = "changed"
        self.args.regen = ["c-2x1"]
        pcm = generate.cache_path(self.args.voice, self.args.model, "answer",
                                  generate.settings_for(self.args.model))
        pcm.parent.mkdir(parents=True)
        pcm.write_bytes(b"pcm")
        self.args.dry_run = True
        with patch.object(generate, "session") as session:
            output = self.build()
        session.assert_not_called()
        self.assertIn("기존 M4A 재사용 0", output)
        self.assertIn("새로 생성 2 (13자)", output)

    def test_changed_identity_does_not_reuse_m4a(self):
        self.args.voice = "other"
        self.args.dry_run = True
        self.assertIn("새로 생성 2 (14자)", self.build())

    def test_model_and_settings_changes_do_not_reuse_m4a(self):
        self.args.dry_run = True
        self.args.model = "eleven_multilingual_v2"
        self.assertIn("새로 생성 2 (14자)", self.build())
        self.args.model = generate.DEFAULT_MODEL
        with patch.object(generate, "settings_for", return_value={"speed": 1.0}):
            self.assertIn("새로 생성 2 (14자)", self.build())

    def test_pcm_cache_avoids_generation(self):
        self.lines["q-2x1"] = "changed"
        pcm = generate.cache_path(self.args.voice, self.args.model, "changed",
                                  generate.settings_for(self.args.model))
        pcm.parent.mkdir(parents=True)
        pcm.write_bytes(b"pcm")
        self.args.dry_run = True
        output = self.build()
        self.assertIn("PCM 캐시 1", output)
        self.assertIn("새로 생성 0 (0자)", output)

    def test_master_failure_preserves_both_packs(self):
        before = {p: p.read_bytes() for base in [generate.IOS_VOICE, generate.ANDROID_VOICE]
                  for p in base.rglob("*") if p.is_file()}
        self.lines.update({"q-2x1": "changed question", "c-2x1": "changed answer"})
        for text in self.lines.values():
            pcm = generate.cache_path(self.args.voice, self.args.model, text,
                                      generate.settings_for(self.args.model))
            pcm.parent.mkdir(parents=True, exist_ok=True)
            pcm.write_bytes(b"pcm")
        def master(_pcm, out):
            out.write_bytes(b"new")
            if out.stem == "c-2x1":
                raise RuntimeError("master failed")
        with patch.object(generate, "session"), patch.object(generate, "ensure_voice"), \
                patch.object(generate, "master", side_effect=master):
            with self.assertRaisesRegex(RuntimeError, "master failed"):
                self.build()
        self.assertEqual(before, {p: p.read_bytes() for p in before})


if __name__ == "__main__":
    unittest.main()
