#!/usr/bin/env python3
"""Self-tests for tools/check_test_results.py.

Each test corresponds to a false conclusion that was actually drawn from Gradle
output, so the gate is itself gated. Run with:

    python -m unittest discover -s tools/tests -v
"""

from __future__ import annotations

import os
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_test_results as gate  # noqa: E402


SINGLE_SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="helium314.keyboard.latin.InputLogicTest" tests="2" skipped="0" failures="1" errors="0">
  <testcase name="passes" classname="helium314.keyboard.latin.InputLogicTest"/>
  <testcase name="breaks" classname="helium314.keyboard.latin.InputLogicTest">
    <failure message="boom">stack</failure>
  </testcase>
</testsuite>
"""

# Gradle writes several <testsuite> elements into one file for
# parameterised/nested classes. Reading only the first one silently drops the
# rest -- exactly the bug this gate exists to catch.
MULTI_SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="helium314.keyboard.ParserTest" tests="1" skipped="0" failures="0" errors="0">
    <testcase name="trivial" classname="helium314.keyboard.ParserTest"/>
  </testsuite>
  <testsuite name="helium314.keyboard.ParserTest" tests="3" skipped="0" failures="2" errors="0">
    <testcase name="canLoadKeyboard" classname="helium314.keyboard.ParserTest">
      <failure message="boom">stack</failure>
    </testcase>
    <testcase name="dvorak has 4 rows" classname="helium314.keyboard.ParserTest">
      <failure message="boom">stack</failure>
    </testcase>
    <testcase name="fine" classname="helium314.keyboard.ParserTest"/>
  </testsuite>
</testsuites>
"""

# declares more tests than it lists -- what a parser miss looks like from outside
INCONSISTENT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="helium314.keyboard.XLinkTest" tests="43" skipped="0" failures="0" errors="0">
  <testcase name="onlyOne" classname="helium314.keyboard.XLinkTest"/>
</testsuite>
"""


def write(dirpath: Path, name: str, content: str) -> Path:
    p = dirpath / name
    p.write_text(content, encoding="utf-8")
    return p


class ParsingTests(unittest.TestCase):
    def test_counts_every_testsuite_in_a_file(self):
        """The regression this gate was built for: 2 of 3 suites must not vanish."""
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            write(d, "TEST-multi.xml", MULTI_SUITE)
            res = gate.parse_results(d)
            self.assertEqual(res.observed_tests, 4, "should see all 4 testcases")
            self.assertEqual(res.observed_failures, 2)
            self.assertIn("ParserTest > canLoadKeyboard", res.failed)
            self.assertIn("ParserTest > dvorak has 4 rows", res.failed)

    def test_aggregates_across_files(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            write(d, "TEST-a.xml", SINGLE_SUITE)
            write(d, "TEST-b.xml", MULTI_SUITE)
            res = gate.parse_results(d)
            self.assertEqual(res.files, 2)
            self.assertEqual(res.observed_tests, 6)
            self.assertEqual(res.observed_failures, 3)

    def test_empty_results_dir_is_an_error(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(SystemExit):
                gate.parse_results(Path(td))


class IntegrityTests(unittest.TestCase):
    def test_self_inconsistency_is_detected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            write(d, "TEST-x.xml", INCONSISTENT)
            res = gate.parse_results(d)
            problems = gate.check_self_consistency(res)
            self.assertTrue(problems, "declared 43 vs enumerated 1 must be flagged")

    def test_stale_results_are_detected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            p = write(d, "TEST-a.xml", SINGLE_SUITE)
            old = time.time() - 3600
            os.utime(p, (old, old))
            self.assertTrue(gate.check_freshness(d, time.time() - 60))

    def test_fresh_results_pass(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            write(d, "TEST-a.xml", SINGLE_SUITE)
            self.assertEqual(gate.check_freshness(d, time.time() - 60), [])


class BaselineTests(unittest.TestCase):
    def _run(self, results_xml, baseline_text, extra=None):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            rd = d / "results"
            rd.mkdir()
            write(rd, "TEST-a.xml", results_xml)
            bl = d / "baseline.txt"
            bl.write_text(baseline_text, encoding="utf-8")
            argv = ["--results-dir", str(rd), "--baseline", str(bl)]
            argv += extra or []
            return gate.main(argv)

    def test_known_failure_passes(self):
        rc = self._run(SINGLE_SUITE, "InputLogicTest > breaks\n")
        self.assertEqual(rc, 0)

    def test_new_failure_fails(self):
        rc = self._run(SINGLE_SUITE, "# nothing known to fail\n")
        self.assertEqual(rc, 1)

    def test_network_dependent_failure_is_not_a_regression(self):
        """A test that reaches the network can flip with no code change."""
        rc = self._run(SINGLE_SUITE, "net:InputLogicTest > breaks\n")
        self.assertEqual(rc, 0)

    def test_stale_results_fail_with_integrity_code(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            rd = d / "results"
            rd.mkdir()
            p = write(rd, "TEST-a.xml", SINGLE_SUITE)
            old = time.time() - 3600
            os.utime(p, (old, old))
            bl = d / "baseline.txt"
            bl.write_text("InputLogicTest > breaks\n", encoding="utf-8")
            rc = gate.main([
                "--results-dir", str(rd),
                "--baseline", str(bl),
                "--started-after", str(time.time() - 60),
            ])
            self.assertEqual(rc, 2, "stale results must not be reported as a pass")

    def test_inconsistent_results_fail_with_integrity_code(self):
        rc = self._run(INCONSISTENT, "")
        self.assertEqual(rc, 2)

    def test_update_baseline_roundtrip(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            rd = d / "results"
            rd.mkdir()
            write(rd, "TEST-a.xml", SINGLE_SUITE)
            bl = d / "baseline.txt"
            self.assertEqual(
                gate.main(["--results-dir", str(rd), "--baseline", str(bl),
                           "--update-baseline"]), 0)
            self.assertEqual(
                gate.main(["--results-dir", str(rd), "--baseline", str(bl)]), 0)


if __name__ == "__main__":
    unittest.main()
