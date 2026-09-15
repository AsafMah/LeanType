#!/usr/bin/env python3
import os
from pathlib import Path
import re
import sys


def main():
    project_root = Path(__file__).resolve().parents[2]
    notes_dir = project_root / "docs" / "releasenote"
    output = notes_dir / "release_notes_temp.md"
    try:
        # Never leave a previous version's notes available after a failed run.
        output.unlink(missing_ok=True)
        gradle = (project_root / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        versions = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"', gradle, re.MULTILINE)
        if len(versions) != 1:
            raise ValueError("Expected one versionName in app/build.gradle.kts")
        version = versions[0]
        if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version):
            raise ValueError(f"Invalid versionName: {version!r}")

        ref = os.environ.get("GITHUB_REF", "")
        if os.environ.get("GITHUB_REF_TYPE") == "tag" or ref.startswith("refs/tags/"):
            tag = os.environ.get("GITHUB_REF_NAME") or ref.removeprefix("refs/tags/")
            if tag != f"v{version}":
                raise ValueError(f"Tag {tag!r} does not match versionName {version!r}")

        source = notes_dir / f"release_notes_v{version}.md"
        content = source.read_bytes()
        lines = content.decode("utf-8").splitlines()
        heading = f"# LeanTypeDual {version}"
        if not lines or lines[0] != heading:
            raise ValueError(f"{source.name} must start with {heading!r}")
        body = re.sub(r"<!--.*?-->", "", "\n".join(lines[1:]), flags=re.DOTALL).strip()
        if not body:
            raise ValueError(f"{source.name} must include a body, not just a heading")
        if re.fullmatch(
            r"(?:TODO|TBD|Coming soon|Release notes for version \S+)[.!]?",
            body, re.IGNORECASE,
        ):
            raise ValueError(f"{source.name} must not contain only placeholder notes")
        output.write_bytes(content)
        print(f"Validated and copied {source.name} to {output.name}")
        return 0
    except (OSError, UnicodeError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
