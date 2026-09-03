#!/usr/bin/env python3
"""Fail when an upstream merge drops a LeanTypeDual product invariant."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED_FLAVORS = {"standard", "standardfull", "offline", "offlinelite"}
LEGACY_SIGNATURE_FLAVORS = {"standard", "standardfull", "offlinelite"}
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
VERSION_CODE_FLOOR = 4300


def _masked(text: str, hide_strings: bool) -> str:
    """Mask comments, and optionally strings, without changing offsets."""
    out = list(text)
    i = 0
    state = "code"
    quote = ""
    while i < len(text):
        if state == "code":
            if text.startswith("//", i):
                out[i:i + 2] = "  "
                i += 2
                state = "line-comment"
            elif text.startswith("/*", i):
                out[i:i + 2] = "  "
                i += 2
                state = "block-comment"
            elif text.startswith('"""', i):
                if hide_strings:
                    out[i:i + 3] = "   "
                i += 3
                state = "triple-string"
            elif text[i] in {'"', "'"}:
                quote = text[i]
                if hide_strings:
                    out[i] = " "
                i += 1
                state = "string"
            else:
                i += 1
        elif state == "line-comment":
            if text[i] == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
        elif state == "block-comment":
            if text.startswith("*/", i):
                out[i:i + 2] = "  "
                i += 2
                state = "code"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        elif state == "triple-string":
            if text.startswith('"""', i):
                if hide_strings:
                    out[i:i + 3] = "   "
                i += 3
                state = "code"
            else:
                if hide_strings and text[i] != "\n":
                    out[i] = " "
                i += 1
        else:
            if text[i] == "\\" and i + 1 < len(text):
                if hide_strings:
                    out[i:i + 2] = "  "
                i += 2
            elif text[i] == quote:
                if hide_strings:
                    out[i] = " "
                i += 1
                state = "code"
            else:
                if hide_strings and text[i] != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def _without_comments(text: str) -> str:
    return _masked(text, hide_strings=False)


def _structure(text: str) -> str:
    return _masked(text, hide_strings=True)


def _matching_delimiter(text: str, opening: int, left: str, right: str) -> int:
    depth = 0
    for i in range(opening, len(text)):
        if text[i] == left:
            depth += 1
        elif text[i] == right:
            depth -= 1
            if depth == 0:
                return i
    return -1


def _extract_block(text: str, header_pattern: str) -> str | None:
    clean = _without_comments(text)
    structure = _structure(text)
    match = re.search(header_pattern, clean, re.MULTILINE)
    if not match:
        return None
    opening = structure.find("{", match.end())
    if opening < 0:
        return None
    closing = _matching_delimiter(structure, opening, "{", "}")
    return None if closing < 0 else text[opening + 1:closing]


def _extract_parenthesized(text: str, header_pattern: str) -> str | None:
    clean = _without_comments(text)
    structure = _structure(text)
    match = re.search(header_pattern, clean, re.MULTILINE)
    if not match:
        return None
    opening = structure.find("(", match.end())
    if opening < 0:
        return None
    closing = _matching_delimiter(structure, opening, "(", ")")
    return None if closing < 0 else text[opening + 1:closing]


def _brace_depth_before(structure: str, offset: int) -> int:
    return structure[:offset].count("{") - structure[:offset].count("}")


def _flavor_blocks(product_flavors: str) -> dict[str, list[str]]:
    clean = _without_comments(product_flavors)
    structure = _structure(product_flavors)
    blocks: dict[str, list[str]] = {}
    pattern = re.compile(r'\bcreate\s*\(\s*"([^"]+)"\s*\)\s*\{')
    for match in pattern.finditer(clean):
        opening = match.end() - 1
        if _brace_depth_before(structure, opening) != 0:
            continue
        closing = _matching_delimiter(structure, opening, "{", "}")
        if closing >= 0:
            blocks.setdefault(match.group(1), []).append(
                product_flavors[opening + 1:closing]
            )
    return blocks


def _string_assignments(block: str, name: str) -> list[str]:
    pattern = rf'(?m)^\s*{re.escape(name)}\s*=\s*"([^"]+)"\s*$'
    return re.findall(pattern, _without_comments(block))


def _integer_assignments(block: str, name: str) -> list[int]:
    pattern = rf"(?m)^\s*{re.escape(name)}\s*=\s*(\d+)\s*$"
    return [int(value) for value in re.findall(pattern, _without_comments(block))]


def _read(root: Path, relative: str, invariant: str, problems: list[str]) -> str | None:
    path = root / relative
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        problems.append(f"[{invariant}] cannot read {relative}: {exc}")
        return None


def _expect_one(values: list[object], expected: object) -> bool:
    return len(values) == 1 and values[0] == expected


def _check_identity_and_flavors(root: Path, problems: list[str]) -> None:
    relative = "app/build.gradle.kts"
    source = _read(root, relative, "gradle", problems)
    if source is None:
        return

    default = _extract_block(source, r"\bdefaultConfig\b")
    if default is None:
        problems.append("[identity] app/build.gradle.kts has no readable defaultConfig block")
        return

    app_ids = _string_assignments(default, "applicationId")
    if not _expect_one(app_ids, "com.asafmah.leantypedual"):
        problems.append(
            "[application-id] defaultConfig.applicationId must be the literal "
            '"com.asafmah.leantypedual"'
        )

    default_min_sdks = _integer_assignments(default, "minSdk")
    if not _expect_one(default_min_sdks, 21):
        problems.append(
            "[flavors/offlinelite] defaultConfig.minSdk must be 21 so offlinelite "
            "inherits API 21 support"
        )

    version_names = _string_assignments(default, "versionName")
    version_codes = _integer_assignments(default, "versionCode")
    if len(version_names) != 1 or not re.fullmatch(r"\d+\.\d+\.\d+", version_names[0]):
        problems.append(
            "[version] defaultConfig.versionName must be one literal SemVer value (X.Y.Z)"
        )
    if len(version_codes) != 1:
        problems.append("[version] defaultConfig.versionCode must be one literal integer")
    if (
        len(version_names) == 1
        and re.fullmatch(r"\d+\.\d+\.\d+", version_names[0])
        and len(version_codes) == 1
    ):
        major, minor, patch = (int(part) for part in version_names[0].split("."))
        code = version_codes[0]
        if major == 4:
            problems.append(
                f"[version] versionName {version_names[0]} looks like the upstream 4.x "
                "release line, not LeanTypeDual's independent version"
            )
        if minor > 9 or patch > 9:
            problems.append(
                "[version] minor and patch must stay single-digit because the documented "
                "versionCode formula allocates one decimal digit to each"
            )
        expected_code = 4000 + major * 1000 + minor * 100 + patch * 10
        changelog_dir = root / "fastlane/metadata/android/en-US/changelogs"
        released_codes = [
            int(path.stem)
            for path in changelog_dir.glob("*.txt")
            if path.stem.isdigit()
        ]
        maintained_floor = max([VERSION_CODE_FLOOR, *released_codes])
        if code < maintained_floor:
            problems.append(
                f"[version] versionCode {code} is below LeanTypeDual's maintained floor "
                f"{maintained_floor} (the newest numeric fastlane changelog)"
            )
        if code != expected_code:
            problems.append(
                f"[version] versionCode {code} does not match LeanTypeDual's documented "
                f"formula for {version_names[0]} (expected {expected_code})"
            )

    product_flavors = _extract_block(source, r"\bproductFlavors\b")
    if product_flavors is None:
        problems.append("[flavors] app/build.gradle.kts has no readable productFlavors block")
    else:
        blocks = _flavor_blocks(product_flavors)
        names = set(blocks)
        if names != EXPECTED_FLAVORS or any(len(items) != 1 for items in blocks.values()):
            problems.append(
                "[flavors] productFlavors must define exactly once: "
                + ", ".join(sorted(EXPECTED_FLAVORS))
                + f"; found: {', '.join(sorted(names)) or 'none'}"
            )
        expected_details = {
            "standard": (23, None),
            "standardfull": (23, None),
            "offline": (26, ".offline"),
            "offlinelite": (None, ".offlinelite"),
        }
        for name, (min_sdk, suffix) in expected_details.items():
            if len(blocks.get(name, [])) != 1:
                continue
            block = blocks[name][0]
            min_sdks = _integer_assignments(block, "minSdk")
            suffixes = _string_assignments(block, "applicationIdSuffix")
            min_ok = not min_sdks if min_sdk is None else _expect_one(min_sdks, min_sdk)
            suffix_ok = not suffixes if suffix is None else _expect_one(suffixes, suffix)
            if not min_ok:
                expected = "inherit defaultConfig.minSdk 21" if min_sdk is None else str(min_sdk)
                problems.append(f"[flavors/{name}] minSdk must {('be ' + expected) if min_sdk is not None else expected}")
            if not suffix_ok:
                expected = "have no applicationIdSuffix" if suffix is None else f'be "{suffix}"'
                problems.append(f"[flavors/{name}] applicationIdSuffix must {expected}")

    _check_dictionary_packaging(source, problems)


def _check_dictionary_packaging(source: str, problems: list[str]) -> None:
    variants = _extract_block(source, r"\bandroidComponents\.onVariants\b")
    if variants is None:
        problems.append(
            "[dictionary-assets] app/build.gradle.kts has no readable "
            "androidComponents.onVariants block"
        )
    else:
        clean = _without_comments(variants)
        structure = _structure(variants)
        guards: list[tuple[int, int]] = []
        for match in re.finditer(r"\bif\s*\(([^)]*)\)\s*\{", clean):
            condition = re.sub(r"\s+", "", match.group(1))
            accepted = {
                'variant.flavorName=="standard"||variant.flavorName=="standardfull"',
                'variant.flavorName=="standardfull"||variant.flavorName=="standard"',
            }
            if condition not in accepted:
                continue
            opening = match.end() - 1
            closing = _matching_delimiter(structure, opening, "{", "}")
            if closing >= 0:
                guards.append((opening + 1, closing))

        markers = [
            'project.file("src/main/assets/dicts")',
            'file.name.endsWith(".dict")',
            "patterns.add(file.name)",
        ]
        guard_ok = False
        if len(guards) == 1:
            start, end = guards[0]
            guarded = re.sub(r"\s+", "", clean[start:end])
            guard_ok = all(re.sub(r"\s+", "", marker) in guarded for marker in markers)
            dict_scans = [m.start() for m in re.finditer(r'endsWith\s*\(\s*"\.dict"\s*\)', clean)]
            guard_ok = guard_ok and len(dict_scans) == 1 and start <= dict_scans[0] < end
        if not guard_ok:
            problems.append(
                "[dictionary-assets] bulk .dict exclusion must be guarded by exactly "
                'variant.flavorName == "standard" || variant.flavorName == "standardfull"; '
                "offline flavors must retain bundled dictionaries"
            )
        assignments = re.findall(
            r"variant\.androidResources\.ignoreAssetsPatterns\s*=\s*patterns\b",
            clean,
        )
        if len(assignments) != 1:
            problems.append(
                "[dictionary-assets] the guarded dictionary patterns must be applied "
                "exactly once via variant.androidResources.ignoreAssetsPatterns"
            )

    clean_source = _without_comments(source)
    dependency = re.compile(
        r'"offlineImplementation"\s*\(\s*'
        r'"io\.github\.ljcamargo:llamacpp-kotlin:0\.4\.0"\s*\)'
    )
    if len(dependency.findall(clean_source)) != 1:
        problems.append(
            "[offline-ai] app/build.gradle.kts must retain exactly one "
            'offlineImplementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")'
        )


def _manifest_permissions(path: Path) -> tuple[int, str | None]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        return 0, str(exc)
    count = 0
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if tag in {"uses-permission", "uses-permission-sdk-23"}:
            count += element.get(ANDROID_NAME) == "android.permission.INTERNET"
    return count, None


def _check_manifests(root: Path, problems: list[str]) -> None:
    src = root / "app/src"
    manifests = sorted(src.glob("*/AndroidManifest.xml")) if src.is_dir() else []
    by_source_set = {path.parent.name: path for path in manifests}

    for source_set in ("standard", "standardfull"):
        path = by_source_set.get(source_set)
        if path is None:
            problems.append(
                f"[internet/{source_set}] app/src/{source_set}/AndroidManifest.xml must "
                "declare android.permission.INTERNET exactly once"
            )
            continue
        count, error = _manifest_permissions(path)
        if error:
            problems.append(f"[internet/{source_set}] cannot parse {path.relative_to(root)}: {error}")
        elif count != 1:
            problems.append(
                f"[internet/{source_set}] {path.relative_to(root)} must declare "
                f"android.permission.INTERNET exactly once; found {count}"
            )

    for source_set, path in by_source_set.items():
        if source_set in {"standard", "standardfull"}:
            continue
        count, error = _manifest_permissions(path)
        if error:
            problems.append(f"[internet/{source_set}] cannot parse {path.relative_to(root)}: {error}")
        elif count:
            problems.append(
                f"[internet/{source_set}] {path.relative_to(root)} must not declare "
                "android.permission.INTERNET"
            )

    main = by_source_set.get("main")
    if main is None:
        problems.append("[initialization-provider] app/src/main/AndroidManifest.xml is missing")
        return
    try:
        manifest = ET.parse(main).getroot()
    except (OSError, ET.ParseError) as exc:
        problems.append(f"[initialization-provider] cannot parse {main.relative_to(root)}: {exc}")
        return
    providers = [
        element
        for element in manifest.iter()
        if element.tag.rsplit("}", 1)[-1] == "provider"
        and element.get(ANDROID_NAME) == "androidx.startup.InitializationProvider"
    ]
    if len(providers) != 1:
        problems.append(
            "[initialization-provider] app/src/main/AndroidManifest.xml must contain "
            f"exactly one androidx.startup.InitializationProvider; found {len(providers)}"
        )


def _class_level_method(source: str, class_name: str, method_name: str) -> str | None:
    class_body = _extract_block(
        source, rf"\bpublic\s+(?:final\s+)?class\s+{re.escape(class_name)}\b[^{{}}]*"
    )
    if class_body is None:
        return None
    clean = _without_comments(class_body)
    structure = _structure(class_body)
    pattern = re.compile(
        rf"\bpublic\s+void\s+{re.escape(method_name)}\s*\(\s*\)\s*\{{"
    )
    methods = []
    for match in pattern.finditer(clean):
        opening = match.end() - 1
        if _brace_depth_before(structure, opening) != 0:
            continue
        closing = _matching_delimiter(structure, opening, "{", "}")
        if closing >= 0:
            methods.append(class_body[opening + 1:closing])
    return methods[0] if len(methods) == 1 else None


def _has_direct_call(method_body: str, call_pattern: str) -> bool:
    clean = _without_comments(method_body)
    structure = _structure(method_body)
    matches = list(re.finditer(call_pattern, clean))
    return (
        len(matches) == 1
        and _brace_depth_before(structure, matches[0].start()) == 0
    )


def _check_sources(root: Path, problems: list[str]) -> None:
    proofread_base = Path(
        "app/src/offlinelite/java/helium314/keyboard/latin/utils"
    )
    for filename in ("ProofreadHelper.kt", "ProofreadService.kt"):
        relative = proofread_base / filename
        if not (root / relative).is_file():
            problems.append(f"[offlinelite-sources] required source is missing: {relative}")

    latin_relative = "app/src/main/java/helium314/keyboard/latin/LatinIME.java"
    latin = _read(root, latin_relative, "latin-ime", problems)
    if latin is not None:
        on_create = _class_level_method(latin, "LatinIME", "onCreate")
        initialize = (
            r"(?:helium314\.keyboard\.latin\.gesture\.)?"
            r"SwipeGestureEngine\.initialize\s*\(\s*this\s*\)\s*;"
        )
        if on_create is None or not _has_direct_call(on_create, initialize):
            problems.append(
                "[latin-ime/on-create] LatinIME.onCreate must directly call "
                "SwipeGestureEngine.initialize(this) exactly once"
            )
        on_destroy = _class_level_method(latin, "LatinIME", "onDestroy")
        cancel = (
            r"(?:helium314\.keyboard\.latin\.gesture\.)?"
            r"SwipeGestureEngine\.cancelIndexing\s*\(\s*\)\s*;"
        )
        if on_destroy is None or not _has_direct_call(on_destroy, cancel):
            problems.append(
                "[latin-ime/on-destroy] LatinIME.onDestroy must directly call "
                "SwipeGestureEngine.cancelIndexing() exactly once"
            )

    settings_relative = (
        "app/src/main/java/helium314/keyboard/settings/SettingsContainer.kt"
    )
    settings = _read(root, settings_relative, "two-thumb-settings", problems)
    if settings is None:
        return
    modules = _extract_parenthesized(
        settings, r"\bprivate\s+val\s+modules\s*=\s*listOf\b"
    )
    matching_modules = 0
    if modules is not None:
        clean = _without_comments(modules)
        structure = _structure(modules)
        for match in re.finditer(r"\bSettingsModule\s*\(", clean):
            opening = match.end() - 1
            closing = _matching_delimiter(structure, opening, "(", ")")
            if closing < 0:
                continue
            arguments = re.sub(r"\s+", "", clean[opening + 1:closing])
            if (
                arguments.startswith(
                    "SettingsWithoutKey.SCREEN_NAV_TWO_THUMB_TYPING,"
                    "SettingsDestination.TwoThumbTyping,"
                )
                and "provider=::createTwoThumbTypingSettings" in arguments
            ):
                matching_modules += 1
    clean_settings = _without_comments(settings)
    constants = re.findall(
        r'(?m)^\s*const\s+val\s+SCREEN_NAV_TWO_THUMB_TYPING\s*=\s*'
        r'"screen_nav_two_thumb_typing"\s*$',
        clean_settings,
    )
    if matching_modules != 1 or len(constants) != 1:
        problems.append(
            "[two-thumb-settings] SettingsContainer.kt must define "
            "SCREEN_NAV_TWO_THUMB_TYPING and register exactly one TwoThumbTyping "
            "SettingsModule with createTwoThumbTypingSettings"
        )


def _workflow_step_run(
    source: str, step_name: str, invariant: str, problems: list[str]
) -> str | None:
    lines = source.splitlines()
    matches: list[tuple[int, int]] = []
    for i, line in enumerate(lines):
        match = re.match(r"^(\s*)-\s+name:\s*(.*?)\s*$", line)
        if match and match.group(2).strip("'\"") == step_name:
            matches.append((i, len(match.group(1))))
    if len(matches) != 1:
        problems.append(
            f"[{invariant}] release workflow must contain exactly one step named "
            f'"{step_name}"; found {len(matches)}'
        )
        return None
    start, indent = matches[0]
    end = len(lines)
    item_prefix = re.compile(rf"^\s{{{indent}}}-\s+")
    for i in range(start + 1, len(lines)):
        if item_prefix.match(lines[i]):
            end = i
            break
    for i in range(start + 1, end):
        match = re.match(r"^(\s*)run:\s*(.*?)\s*$", lines[i])
        if not match:
            continue
        value = match.group(2)
        if value and value not in {"|", "|-"}:
            return value
        if value not in {"|", "|-"}:
            problems.append(
                f"[{invariant}] step \"{step_name}\" must use an inline or literal run block"
            )
            return None
        return "\n".join(lines[i + 1:end])
    problems.append(f'[{invariant}] step "{step_name}" has no run command')
    return None


def _strip_shell_comments(source: str) -> str:
    cleaned = []
    for line in source.splitlines():
        quote = ""
        escaped = False
        code = []
        for char in line:
            if escaped:
                code.append(char)
                escaped = False
            elif char == "\\":
                code.append(char)
                escaped = True
            elif quote:
                code.append(char)
                if char == quote:
                    quote = ""
            elif char in {"'", '"'}:
                code.append(char)
                quote = char
            elif char == "#":
                break
            else:
                code.append(char)
        cleaned.append("".join(code))
    return "\n".join(cleaned)


def _check_release_workflow(root: Path, problems: list[str]) -> None:
    relative = ".github/workflows/release.yml"
    source = _read(root, relative, "release", problems)
    if source is None:
        return

    build = _workflow_step_run(
        source, "Build signed release APKs (all flavors)", "release/build-flavors", problems
    )
    if build is not None:
        tasks = re.findall(
            r":app:assemble([A-Za-z0-9]+)Release\b", _strip_shell_comments(build)
        )
        flavors = {task.lower() for task in tasks}
        if flavors != EXPECTED_FLAVORS or len(tasks) != len(EXPECTED_FLAVORS):
            problems.append(
                "[release/build-flavors] release build step must execute exactly one "
                "Release assemble task for each of: "
                + ", ".join(sorted(EXPECTED_FLAVORS))
            )

    verify = _workflow_step_run(
        source, "Verify release APK signatures", "release/legacy-signatures", problems
    )
    if verify is None:
        return
    verify = _strip_shell_comments(verify)
    lines = verify.splitlines()
    legacy_arms: list[tuple[set[str], str]] = []
    for i, line in enumerate(lines):
        flavors = set(re.findall(r"\*-(standard|standardfull|offline|offlinelite)-release\.apk", line))
        if not flavors or ")" not in line:
            continue
        end = next((j for j in range(i + 1, len(lines)) if ";;" in lines[j]), -1)
        if end >= 0:
            legacy_arms.append((flavors, "\n".join(lines[i + 1:end])))
    matching_arms = [
        body for flavors, body in legacy_arms if flavors == LEGACY_SIGNATURE_FLAVORS
    ]
    low_api_ok = False
    if len(matching_arms) == 1:
        for line in matching_arms[0].splitlines():
            if (
                "APKSIGNER" in line
                and re.search(r"\bverify\b", line)
                and re.search(r"--min-sdk-version\s+21\b", line)
                and re.search(r"--max-sdk-version\s+23\b", line)
                and re.search(r'["\']?\$apk["\']?', line)
            ):
                low_api_ok = True
                break
    count_ok = re.search(r'test\s+["\']?\$count["\']?\s+-eq\s+4\b', verify)
    legacy_count_ok = re.search(
        r'test\s+["\']?\$legacy_count["\']?\s+-eq\s+3\b', verify
    )
    loop_ok = re.search(
        r"for\s+apk\s+in\s+app/build/outputs/apk/\*/release/\*\.apk\s*;", verify
    )
    if not (low_api_ok and count_ok and legacy_count_ok and loop_ok):
        problems.append(
            "[release/legacy-signatures] signature step must inspect exactly four "
            "release APKs and run apksigner with min SDK 21 / max SDK 23 for exactly "
            "standard, standardfull, and offlinelite (the v1/JAR-signature variants)"
        )

    packaged = _workflow_step_run(
        source,
        "Verify packaged LeanTypeDual invariants",
        "release/packaged-invariants",
        problems,
    )
    if packaged is not None:
        command = re.sub(r"\s+", " ", _strip_shell_comments(packaged)).strip()
        required = (
            r'python3 tools/check_apk_invariants\.py '
            r'--apk-dir app/build/outputs/apk '
            r'--apkanalyzer ["\']?\$APKANALYZER["\']?'
        )
        if not re.search(required, command):
            problems.append(
                "[release/packaged-invariants] packaged invariant step must run "
                "tools/check_apk_invariants.py against app/build/outputs/apk with "
                "the resolved APKANALYZER executable"
            )


def check_repo(root: Path) -> list[str]:
    root = root.resolve()
    problems: list[str] = []
    _check_identity_and_flavors(root, problems)
    _check_manifests(root, problems)
    _check_sources(root, problems)
    _check_release_workflow(root, problems)
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="repository root (defaults to the parent of tools/)",
    )
    args = parser.parse_args(argv)
    problems = check_repo(args.root)
    if problems:
        print(f"LeanTypeDual fork invariant gate failed ({len(problems)} violation(s)):")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("[ok] LeanTypeDual fork invariants hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
