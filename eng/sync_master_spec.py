#!/usr/bin/env python3
"""Generate .ai-agent/master-implementation-spec.md from source documentation.

Edit the source files, not the generated master document.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".ai-agent/master-implementation-spec.md"

ROOT_SOURCES = [
    "README.md",
    "AGENTS.md",
    ".ai-agent/start-here.md",
    "PROJECT_STATUS.md",
    "IMPLEMENTATION_CHECKLIST.md",
]

def source_paths():
    paths = [ROOT / p for p in ROOT_SOURCES]
    paths.extend(sorted((ROOT / "docs").glob("[0-9][0-9]_*.md")))
    paths.extend(sorted((ROOT / "docs" / "decisions").glob("ADR-*.md")))
    paths.append(ROOT / "templates" / "definition-of-done.md")
    paths.append(ROOT / ".ai-agent/prompt-sequence.md")
    paths.extend(sorted((ROOT / ".ai-agent/prompts").glob("*.md")))
    return paths

def main():
    sections = [
        "# LaunchForge - AI Agent Master Implementation Specification",
        "",
        "> GENERATED FILE. Source files are the authority. Run `python eng/sync_master_spec.py` after editing documentation.",
        "",
    ]
    for path in source_paths():
        if not path.exists():
            raise SystemExit(f"Missing source: {path.relative_to(ROOT)}")
        rel = path.relative_to(ROOT).as_posix()
        sections += [
            "---",
            "",
            f"<!-- SOURCE: {rel} -->",
            "",
            path.read_text(encoding="utf-8").rstrip(),
            "",
        ]
    OUT.write_text("\n".join(sections).rstrip() + "\n", encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")

if __name__ == "__main__":
    main()
