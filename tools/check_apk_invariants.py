#!/usr/bin/env python3
"""Verify LeanTypeDual invariants in assembled release APKs."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import zipfile
from collections.abc import Callable
from pathlib import Path


EXPECTED = {
    "standard": ("com.asafmah.leantypedual", 23, True, False),
    "standardfull": ("com.asafmah.leantypedual", 23, True, False),
    "offline": ("com.asafmah.leantypedual.offline", 26, False, True),
    "offlinelite": ("com.asafmah.leantypedual.offlinelite", 21, False, True),
}
APK_NAME = re.compile(
    r"-(standard|standardfull|offline|offlinelite)-release\.apk$", re.IGNORECASE
)
INTERNET = "android.permission.INTERNET"
Analyzer = Callable[[str, Path], str]


class AnalyzerError(RuntimeError):
    pass


def subprocess_analyzer(executable: str) -> Analyzer:
    def run(operation: str, apk: Path) -> str:
        try:
            result = subprocess.run(
                [executable, "manifest", operation, str(apk)],
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
        except OSError as exc:
            raise AnalyzerError(f"cannot run {executable}: {exc}") from exc
        if result.returncode:
            detail = result.stderr.strip() or result.stdout.strip() or "no diagnostic"
            raise AnalyzerError(
                f"{executable} manifest {operation} failed for {apk.name}: {detail}"
            )
        return result.stdout

    return run


def resolve_apkanalyzer(explicit: str | None) -> str:
    if explicit:
        return explicit
    on_path = shutil.which("apkanalyzer")
    if on_path:
        return on_path
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if not sdk:
            continue
        command_line_tools = Path(sdk) / "cmdline-tools"
        preferred = [
            command_line_tools / "latest/bin/apkanalyzer",
            command_line_tools / "latest/bin/apkanalyzer.bat",
        ]
        candidates = preferred + sorted(command_line_tools.glob("*/bin/apkanalyzer*"), reverse=True)
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    return "apkanalyzer"


def _discover(apk_dir: Path, problems: list[str]) -> dict[str, Path]:
    if not apk_dir.is_dir():
        problems.append(f"[apk/set] APK directory does not exist: {apk_dir}")
        return {}
    apks = sorted(
        path for path in apk_dir.rglob("*.apk")
        if path.name.lower().endswith("-release.apk")
    )
    grouped: dict[str, list[Path]] = {flavor: [] for flavor in EXPECTED}
    unmatched = []
    for apk in apks:
        match = APK_NAME.search(apk.name)
        if match:
            grouped[match.group(1).lower()].append(apk)
        else:
            unmatched.append(apk.name)
    wrong_counts = {
        flavor: len(paths) for flavor, paths in grouped.items() if len(paths) != 1
    }
    if len(apks) != len(EXPECTED) or unmatched or wrong_counts:
        details = ", ".join(
            f"{flavor}={len(grouped[flavor])}" for flavor in sorted(grouped)
        )
        if unmatched:
            details += f"; unrecognized={','.join(unmatched)}"
        problems.append(
            "[apk/set] expected exactly four release APKs, one per flavor "
            f"(standard, standardfull, offline, offlinelite); found {details}"
        )
    return {
        flavor: paths[0]
        for flavor, paths in grouped.items()
        if len(paths) == 1
    }


def _zip_entries(apk: Path) -> set[str]:
    try:
        with zipfile.ZipFile(apk) as archive:
            return {name.lstrip("/") for name in archive.namelist()}
    except (OSError, zipfile.BadZipFile) as exc:
        raise AnalyzerError(f"cannot inspect ZIP contents of {apk.name}: {exc}") from exc


def _permissions(output: str) -> set[str]:
    return set(re.findall(r"\bandroid\.permission\.[A-Za-z0-9_.]+", output))


def check_apks(apk_dir: Path, analyzer: Analyzer) -> list[str]:
    problems: list[str] = []
    apks = _discover(apk_dir, problems)
    for flavor, (expected_id, expected_min_sdk, needs_internet, needs_dict) in EXPECTED.items():
        apk = apks.get(flavor)
        if apk is None:
            continue
        try:
            entries = _zip_entries(apk)
        except AnalyzerError as exc:
            problems.append(f"[apk/{flavor}/assets] {exc}")
            entries = set()
        dictionaries = {
            entry
            for entry in entries
            if entry.startswith("assets/dicts/") and entry.endswith(".dict")
        }
        if needs_dict and "assets/dicts/main_en-US.dict" not in dictionaries:
            problems.append(
                f"[apk/{flavor}/dictionaries] {apk.name} must contain "
                "assets/dicts/main_en-US.dict"
            )
        if not needs_dict and dictionaries:
            sample = ", ".join(sorted(dictionaries)[:3])
            problems.append(
                f"[apk/{flavor}/dictionaries] {apk.name} must not package .dict files "
                f"under assets/dicts/ (found {sample})"
            )

        try:
            application_id = analyzer("application-id", apk).strip()
            min_sdk_text = analyzer("min-sdk", apk).strip()
            permission_set = _permissions(analyzer("permissions", apk))
        except AnalyzerError as exc:
            problems.append(f"[apk/{flavor}/manifest] {exc}")
            continue

        if application_id != expected_id:
            problems.append(
                f"[apk/{flavor}/application-id] expected {expected_id}, "
                f"found {application_id or 'empty output'}"
            )
        if not re.fullmatch(r"\d+", min_sdk_text):
            problems.append(
                f"[apk/{flavor}/min-sdk] apkanalyzer returned a non-integer minSdk: "
                f"{min_sdk_text or 'empty output'}"
            )
        elif int(min_sdk_text) != expected_min_sdk:
            problems.append(
                f"[apk/{flavor}/min-sdk] expected {expected_min_sdk}, "
                f"found {min_sdk_text}"
            )
        has_internet = INTERNET in permission_set
        if has_internet != needs_internet:
            expected = "declare" if needs_internet else "not declare"
            problems.append(
                f"[apk/{flavor}/internet] {apk.name} must {expected} {INTERNET}"
            )
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument(
        "--apkanalyzer",
        default=None,
        help="apkanalyzer executable (auto-detected from PATH or the Android SDK)",
    )
    args = parser.parse_args(argv)
    analyzer = resolve_apkanalyzer(args.apkanalyzer)
    problems = check_apks(args.apk_dir, subprocess_analyzer(analyzer))
    if problems:
        print(f"LeanTypeDual APK invariant gate failed ({len(problems)} violation(s)):")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("[ok] packaged LeanTypeDual APK invariants hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
