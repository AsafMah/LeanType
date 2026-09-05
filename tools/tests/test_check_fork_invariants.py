#!/usr/bin/env python3
"""Mutation tests for the LeanTypeDual fork invariant gate."""

from __future__ import annotations

import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_fork_invariants as gate  # noqa: E402


FIXTURE = Path(__file__).parent / "fixtures" / "fork_invariants"
REPO_ROOT = Path(__file__).resolve().parents[2]


class WorkflowPathTests(unittest.TestCase):
    def test_gate_and_workflow_changes_trigger_their_tests(self):
        workflows = {
            "build-test-auto.yml": (
                "tools/check_test_results.py",
                "tools/test_baselines/**",
            ),
            "native-tests.yml": (".github/workflows/native-tests.yml",),
        }
        for workflow, paths in workflows.items():
            source = (REPO_ROOT / ".github" / "workflows" / workflow).read_text(encoding="utf-8")
            for event in ("push", "pull_request"):
                with self.subTest(workflow=workflow, event=event):
                    block = re.search(rf"(?ms)^  {event}:\n(.*?)(?=^ {{0,2}}\S|\Z)", source)
                    self.assertIsNotNone(block, f"missing {event} trigger")
                    paths_block = re.search(r"(?ms)^    paths:(.*?)(?=^    \S|\Z)", block[1])
                    self.assertIsNotNone(paths_block, f"missing {event} paths")
                    for path in paths:
                        self.assertIn(f"'{path}'", paths_block[1])


class ForkInvariantTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name) / "repo"
        shutil.copytree(FIXTURE, self.root)

    def tearDown(self):
        self.temp_dir.cleanup()

    def mutate(self, relative: str, old: str, new: str) -> None:
        path = self.root / relative
        source = path.read_text(encoding="utf-8")
        self.assertIn(old, source, f"fixture no longer contains mutation target in {relative}")
        path.write_text(source.replace(old, new, 1), encoding="utf-8")

    def assert_violation(self, invariant: str) -> None:
        problems = gate.check_repo(self.root)
        self.assertTrue(
            any(problem.startswith(f"[{invariant}]") for problem in problems),
            f"expected [{invariant}], got: {problems}",
        )

    def test_valid_fixture_passes(self):
        self.assertEqual(gate.check_repo(self.root), [])

    def test_real_repository_passes(self):
        self.assertEqual(gate.check_repo(REPO_ROOT), [])

    def test_missing_offlinelite_flavor_fails(self):
        self.mutate(
            "app/build.gradle.kts",
            '''        create("offlinelite") {
            dimension = "privacy"
            applicationIdSuffix = ".offlinelite"
        }
''',
            "",
        )
        self.assert_violation("flavors")

    def test_offline_min_sdk_21_fails(self):
        self.mutate("app/build.gradle.kts", "            minSdk = 26", "            minSdk = 21")
        self.assert_violation("flavors/offline")

    def test_version_cannot_regress_below_released_metadata(self):
        released = self.root / "fastlane/metadata/android/en-US/changelogs/4400.txt"
        released.write_text("Newer released version\n", encoding="utf-8")
        self.assert_violation("version")

    def test_internet_in_main_or_offline_fails(self):
        permission = '    <uses-permission android:name="android.permission.INTERNET" />\n'
        with self.subTest(source_set="main"):
            self.mutate(
                "app/src/main/AndroidManifest.xml",
                "<application>",
                permission + "    <application>",
            )
            self.assert_violation("internet/main")
        shutil.rmtree(self.root)
        shutil.copytree(FIXTURE, self.root)
        with self.subTest(source_set="offline"):
            offline = self.root / "app/src/offline/AndroidManifest.xml"
            offline.parent.mkdir(parents=True)
            offline.write_text(
                '<?xml version="1.0" encoding="utf-8"?>\n'
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f"{permission}</manifest>\n",
                encoding="utf-8",
            )
            self.assert_violation("internet/offline")

    def test_removed_two_thumb_module_fails(self):
        self.mutate(
            "app/src/main/java/helium314/keyboard/settings/SettingsContainer.kt",
            '''    SettingsModule(
        SettingsWithoutKey.SCREEN_NAV_TWO_THUMB_TYPING,
        SettingsDestination.TwoThumbTyping,
        provider = ::createTwoThumbTypingSettings,
    ),
''',
            "",
        )
        self.assert_violation("two-thumb-settings")

    def test_duplicate_initialization_provider_fails(self):
        provider = (
            '        <provider android:name="androidx.startup.InitializationProvider" '
            'android:authorities="${applicationId}.startup2" />\n'
        )
        self.mutate(
            "app/src/main/AndroidManifest.xml",
            "    </application>",
            provider + "    </application>",
        )
        self.assert_violation("initialization-provider")

    def test_release_build_missing_flavor_fails(self):
        self.mutate(
            ".github/workflows/release.yml",
            " :app:assembleOfflineliteRelease",
            "",
        )
        self.assert_violation("release/build-flavors")

    def test_release_packaged_gate_step_is_required(self):
        self.mutate(
            ".github/workflows/release.yml",
            '''      - name: Verify packaged LeanTypeDual invariants
        run: |
          APKANALYZER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer"
          python3 tools/check_apk_invariants.py --apk-dir app/build/outputs/apk --apkanalyzer "$APKANALYZER"

''',
            "",
        )
        self.assert_violation("release/packaged-invariants")

    def test_unscoped_dictionary_exclusion_fails(self):
        self.mutate(
            "app/build.gradle.kts",
            '        if (variant.flavorName == "standard" || variant.flavorName == "standardfull") {',
            "        if (true) {",
        )
        self.assert_violation("dictionary-assets")

    def test_dictionary_exclusion_must_be_applied(self):
        self.mutate(
            "app/build.gradle.kts",
            "            variant.androidResources.ignoreAssetsPatterns = patterns",
            "            println(patterns)",
        )
        self.assert_violation("dictionary-assets")

    def test_missing_offline_llama_dependency_fails(self):
        self.mutate(
            "app/build.gradle.kts",
            '    "offlineImplementation"("io.github.ljcamargo:llamacpp-kotlin:0.4.0")\n',
            "",
        )
        self.assert_violation("offline-ai")


if __name__ == "__main__":
    unittest.main()
