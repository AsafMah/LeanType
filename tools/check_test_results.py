#!/usr/bin/env python3
"""Integrity + baseline gate for Gradle JUnit XML test results.

This exists because three separate false conclusions were drawn from Gradle test
output during one session, none of which the test run itself flagged:

1. Gradle served results from a previous run because the test task was
   UP-TO-DATE, so a "passing" report described code that was never exercised.
2. A hand-rolled XML reader silently under-counted: it disagreed with the
   totals the results themselves declared, and reported 4 failures where there
   were 12. The exact mechanism is less important than the fact that nothing
   flagged it -- under-reporting is the dangerous direction, because it looks
   like good news. So this script never trusts its own enumeration: it counts
   <testcase> elements AND sums the tests=/failures= attributes the suites
   declare, and refuses to report anything if the two disagree.
3. Failures were attributed to a code change when at least one of them reaches
   the network and can flip with no code change at all.

Each check below is one of those, made mechanical. Run it after a test task;
it exits non-zero rather than relying on anyone remembering.

Usage:
    python tools/check_test_results.py \
        --results-dir app/build/test-results/testOfflineRunTestsUnitTest \
        --baseline tools/test_baselines/runTests-linux.txt \
        --started-after 1723800000

    # after deliberately changing which tests fail:
    python tools/check_test_results.py --results-dir ... --baseline ... --update-baseline

Exit codes:
    0  results are trustworthy and match the baseline
    1  baseline mismatch (new failures)
    2  integrity failure (stale, unparseable, or self-inconsistent results)
"""

from __future__ import annotations

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

NET_PREFIX = "net:"


@dataclass
class Results:
    """Everything parsed out of one results directory."""

    # test ids that failed or errored, as "ClassName > test name"
    failed: set[str] = field(default_factory=set)
    # declared totals, summed from <testsuite> attributes
    declared_tests: int = 0
    declared_failures: int = 0
    # observed totals, counted from <testcase> elements
    observed_tests: int = 0
    observed_failures: int = 0
    suites: int = 0
    files: int = 0


def parse_results(results_dir: Path) -> Results:
    """Parse every <testsuite> in every XML file under results_dir.

    Deliberately iterates all testsuite elements, not just the document root:
    a single file may hold more than one, and missing them silently
    under-reports failures.
    """
    res = Results()
    xml_files = sorted(results_dir.glob("**/*.xml"))
    if not xml_files:
        raise SystemExit(f"[integrity] no result XML found under {results_dir}")

    for path in xml_files:
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            raise SystemExit(f"[integrity] cannot parse {path}: {exc}")
        res.files += 1

        root = tree.getroot()
        suites = [root] if root.tag == "testsuite" else []
        suites.extend(root.iter("testsuite") if root.tag != "testsuite" else [])
        # a root <testsuite> may itself nest further <testsuite> children
        if root.tag == "testsuite":
            suites.extend(root.findall("testsuite"))

        seen = set()
        for suite in suites:
            if id(suite) in seen:
                continue
            seen.add(id(suite))
            res.suites += 1
            res.declared_tests += int(suite.get("tests", 0))
            res.declared_failures += int(suite.get("failures", 0)) + int(
                suite.get("errors", 0)
            )
            suite_name = (suite.get("name") or path.stem).split(".")[-1]

            for case in suite.findall("testcase"):
                res.observed_tests += 1
                if case.find("failure") is not None or case.find("error") is not None:
                    res.observed_failures += 1
                    res.failed.add(f"{suite_name} > {case.get('name')}")

    return res


def check_freshness(results_dir: Path, started_after: float) -> list[str]:
    """Every result file must post-date the run we think produced it."""
    problems = []
    for path in sorted(results_dir.glob("**/*.xml")):
        mtime = path.stat().st_mtime
        if mtime < started_after:
            problems.append(
                f"  {path.name} last written {mtime:.0f}, before run start {started_after:.0f}"
            )
    return problems


def check_self_consistency(res: Results) -> list[str]:
    """Declared totals must equal what we actually enumerated.

    This is the check that catches an under-counting reader: whatever the
    mechanism (a skipped suite, a results file read while still being written,
    an unexpected root element), the enumerated counts come out lower than the
    totals the suites declare, and that disagreement is mechanically visible.
    """
    problems = []
    if res.declared_tests != res.observed_tests:
        problems.append(
            f"  declared {res.declared_tests} tests but enumerated {res.observed_tests}"
        )
    if res.declared_failures != res.observed_failures:
        problems.append(
            f"  declared {res.declared_failures} failures but enumerated "
            f"{res.observed_failures}"
        )
    return problems


def load_baseline(path: Path) -> tuple[set[str], set[str]]:
    """Return (expected_failures, network_dependent)."""
    expected: set[str] = set()
    networked: set[str] = set()
    if not path.exists():
        return expected, networked
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith(NET_PREFIX):
            networked.add(line[len(NET_PREFIX):].strip())
        else:
            expected.add(line)
    return expected, networked


def write_baseline(path: Path, failed: set[str], networked: set[str]) -> None:
    lines = [
        "# Known-failing tests. Generated by tools/check_test_results.py.",
        "# One test id per line, as reported: 'ClassName > test name'.",
        "# Prefix a line with 'net:' if the test reaches the network -- those are",
        "# reported but never treated as a regression or as an attributable fix,",
        "# because they can flip without any code change.",
        "",
    ]
    lines += sorted(failed - networked)
    if networked:
        lines.append("")
        lines += [f"{NET_PREFIX}{name}" for name in sorted(networked)]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--results-dir", required=True, type=Path)
    ap.add_argument("--baseline", required=True, type=Path)
    ap.add_argument(
        "--started-after",
        type=float,
        default=None,
        help="epoch seconds; every result file must be newer than this",
    )
    ap.add_argument("--update-baseline", action="store_true")
    args = ap.parse_args(argv)

    if not args.results_dir.is_dir():
        print(f"[integrity] results dir does not exist: {args.results_dir}")
        return 2

    # 1. staleness
    if args.started_after is not None:
        stale = check_freshness(args.results_dir, args.started_after)
        if stale:
            print("[integrity] STALE RESULTS -- the test task did not rerun:")
            print("\n".join(stale))
            print("  re-run with --rerun-tasks, or pass the correct --started-after")
            return 2

    res = parse_results(args.results_dir)

    # 2. self-consistency
    inconsistent = check_self_consistency(res)
    if inconsistent:
        print("[integrity] RESULTS DISAGREE WITH THEMSELVES:")
        print("\n".join(inconsistent))
        print(
            "  the parser missed testcases -- most likely a file holding more than\n"
            "  one <testsuite>. Do not trust any count from this run."
        )
        return 2

    print(
        f"[ok] {res.files} file(s), {res.suites} suite(s), "
        f"{res.observed_tests} tests, {res.observed_failures} failed"
    )

    expected, networked = load_baseline(args.baseline)

    if args.update_baseline:
        write_baseline(args.baseline, res.failed, networked & res.failed)
        print(f"[ok] baseline written to {args.baseline} ({len(res.failed)} entries)")
        return 0

    # 3. baseline diff, by NAME, with network-dependent tests quarantined
    new_failures = res.failed - expected - networked
    fixed = expected - res.failed
    net_failing = res.failed & networked

    if net_failing:
        print("[note] network-dependent tests failing (not counted either way):")
        for name in sorted(net_failing):
            print(f"  {name}")

    if fixed:
        print("[note] no longer failing -- refresh the baseline if deliberate:")
        for name in sorted(fixed):
            print(f"  {name}")

    if new_failures:
        print(f"[FAIL] {len(new_failures)} test(s) failing that the baseline does not list:")
        for name in sorted(new_failures):
            print(f"  {name}")
        return 1

    print("[ok] no new failures against baseline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
