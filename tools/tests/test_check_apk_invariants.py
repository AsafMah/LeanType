#!/usr/bin/env python3
"""Fixture-driven tests for packaged LeanTypeDual APK invariants."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_apk_invariants as gate  # noqa: E402


FIXTURE = Path(__file__).parent / "fixtures" / "apk_invariants.json"


class ApkInvariantTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.apk_dir = Path(self.temp_dir.name) / "apks"
        self.apk_dir.mkdir()
        self.metadata = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self._write_apks()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _write_apks(self):
        for apk in self.apk_dir.glob("*.apk"):
            apk.unlink()
        for flavor, metadata in self.metadata.items():
            path = self.apk_dir / f"1-LeanTypeDual_0.3.0-{flavor}-release.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
                for entry in metadata["files"]:
                    archive.writestr(entry, b"dictionary")

    def _analyzer(self, operation: str, apk: Path) -> str:
        match = gate.APK_NAME.search(apk.name)
        self.assertIsNotNone(match)
        metadata = self.metadata[match.group(1).lower()]
        if operation == "application-id":
            return metadata["applicationId"] + "\n"
        if operation == "min-sdk":
            return str(metadata["minSdk"]) + "\n"
        if operation == "permissions":
            return "\n".join(metadata["permissions"]) + "\n"
        raise AssertionError(f"unexpected operation: {operation}")

    def assert_violation(self, invariant: str):
        problems = gate.check_apks(self.apk_dir, self._analyzer)
        self.assertTrue(
            any(problem.startswith(f"[{invariant}]") for problem in problems),
            f"expected [{invariant}], got: {problems}",
        )

    def test_valid_release_set_passes(self):
        self.assertEqual(gate.check_apks(self.apk_dir, self._analyzer), [])

    def test_missing_flavor_fails(self):
        next(self.apk_dir.glob("*-offlinelite-release.apk")).unlink()
        self.assert_violation("apk/set")

    def test_extra_debug_apk_is_ignored(self):
        with zipfile.ZipFile(self.apk_dir / "LeanTypeDual-standard-debug.apk", "w") as archive:
            archive.writestr("AndroidManifest.xml", b"debug")
        self.assertEqual(gate.check_apks(self.apk_dir, self._analyzer), [])

    def test_unknown_release_apk_fails(self):
        with zipfile.ZipFile(self.apk_dir / "LeanTypeDual-unknown-release.apk", "w") as archive:
            archive.writestr("AndroidManifest.xml", b"unknown")
        self.assert_violation("apk/set")

    def test_nested_dictionary_in_standard_fails(self):
        apk = next(self.apk_dir.glob("*-standard-release.apk"))
        with zipfile.ZipFile(apk, "a") as archive:
            archive.writestr("assets/dicts/nested/should-not-ship.dict", b"dictionary")
        self.assert_violation("apk/standard/dictionaries")

    def test_offline_requires_main_english_dictionary(self):
        self.metadata["offline"]["files"] = ["assets/dicts/main_de.dict"]
        self._write_apks()
        self.assert_violation("apk/offline/dictionaries")

    def test_internet_permission_matches_privacy_tier(self):
        with self.subTest(flavor="standard"):
            self.metadata["standard"]["permissions"] = []
            self.assert_violation("apk/standard/internet")
        self.metadata = json.loads(FIXTURE.read_text(encoding="utf-8"))
        with self.subTest(flavor="offlinelite"):
            self.metadata["offlinelite"]["permissions"] = ["android.permission.INTERNET"]
            self.assert_violation("apk/offlinelite/internet")

    def test_application_id_is_effective_packaged_id(self):
        self.metadata["offline"]["applicationId"] = "com.asafmah.leantypedual"
        self.assert_violation("apk/offline/application-id")

    def test_min_sdk_is_effective_packaged_value(self):
        self.metadata["offline"]["minSdk"] = 21
        self.assert_violation("apk/offline/min-sdk")


if __name__ == "__main__":
    unittest.main()
