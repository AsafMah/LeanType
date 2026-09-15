import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "app").mkdir()
        scripts = self.root / "docs" / "scripts"
        scripts.mkdir(parents=True)
        self.script = scripts / "generate_release_notes.py"
        shutil.copyfile(Path(__file__).with_name(self.script.name), self.script)
        self.gradle = self.root / "app" / "build.gradle.kts"
        self.gradle.write_text('versionName = "2.0.0"\n', encoding="utf-8")
        self.notes_dir = self.root / "docs" / "releasenote"
        self.notes_dir.mkdir()
        self.source = self.notes_dir / "release_notes_v2.0.0.md"
        self.content = "# LeanTypeDual 2.0.0\n\nEditable swipe shortcuts.\n"
        self.source.write_text(self.content, encoding="utf-8")
        self.output = self.notes_dir / "release_notes_temp.md"

    def run_script(self, ref_type="", ref_name="", ref=""):
        env = dict(os.environ, GITHUB_REF_TYPE=ref_type,
                   GITHUB_REF_NAME=ref_name, GITHUB_REF=ref)
        return subprocess.run(
            [sys.executable, str(self.script)], env=env,
            capture_output=True, text=True, encoding="utf-8",
        )

    def assert_failed(self, result, message):
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn(message, result.stderr)
        self.assertFalse(self.output.exists(), "Failed run left publishable notes")

    def test_local_build_uses_gradle_version(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_bytes(), self.source.read_bytes())

    def test_v2_branch_is_not_a_version_tag(self):
        result = self.run_script("branch", "v2", "refs/heads/v2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_bytes(), self.source.read_bytes())

    def test_version_shaped_branch_still_uses_gradle(self):
        result = self.run_script("branch", "v9.9.9", "refs/heads/v9.9.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_bytes(), self.source.read_bytes())

    def test_matching_tag(self):
        result = self.run_script("tag", "v2.0.0", "refs/tags/v2.0.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_bytes(), self.source.read_bytes())

    def test_tag_ref_without_ref_type_still_checks_version(self):
        self.assert_failed(
            self.run_script(ref_name="v2.0.1", ref="refs/tags/v2.0.1"),
            "does not match",
        )

    def test_mismatched_tag_rejects_stale_output(self):
        self.output.write_text("Stale notes", encoding="utf-8")
        self.assert_failed(self.run_script("tag", "v2.0.1"), "does not match")

    def test_incomplete_version_tag_rejected(self):
        self.assert_failed(self.run_script("tag", "v2"), "does not match")

    def test_missing_notes(self):
        self.source.unlink()
        self.assert_failed(self.run_script(), "No such file")

    def test_empty_notes(self):
        self.source.write_text(" \n", encoding="utf-8")
        self.assert_failed(self.run_script(), "must start with")

    def test_placeholder_notes(self):
        self.source.write_text("Release notes for version 2.0.0", encoding="utf-8")
        self.assert_failed(self.run_script(), "must start with")

    def test_heading_only_notes(self):
        self.source.write_text("# LeanTypeDual 2.0.0\n\n", encoding="utf-8")
        self.assert_failed(self.run_script(), "must include a body")

    def test_placeholder_body_under_valid_heading(self):
        for body in ("TODO", "TBD", "Coming soon.", "Release notes for version 2.0.0"):
            with self.subTest(body=body):
                self.source.write_text(f"# LeanTypeDual 2.0.0\n\n{body}\n", encoding="utf-8")
                self.assert_failed(self.run_script(), "placeholder")

    def test_comment_only_body_under_valid_heading(self):
        self.source.write_text(
            "# LeanTypeDual 2.0.0\n\n<!-- TODO:\nwrite notes -->\n",
            encoding="utf-8",
        )
        self.assert_failed(self.run_script(), "must include a body")

    def test_wrong_version_in_heading(self):
        self.source.write_text("# LeanTypeDual 2.0.1\n\nChanges\n", encoding="utf-8")
        self.assert_failed(self.run_script(), "must start with")

    def test_invalid_gradle_version(self):
        self.gradle.write_text('versionName = "2"\n', encoding="utf-8")
        self.assert_failed(self.run_script(), "Invalid versionName")

    def test_missing_gradle_version(self):
        self.gradle.write_text("// No version\n", encoding="utf-8")
        self.assert_failed(self.run_script(), "Expected one versionName")

    def test_tag_cannot_replace_missing_gradle(self):
        self.gradle.unlink()
        self.assert_failed(self.run_script("tag", "v2.0.0"), "No such file")


if __name__ == "__main__":
    unittest.main()
