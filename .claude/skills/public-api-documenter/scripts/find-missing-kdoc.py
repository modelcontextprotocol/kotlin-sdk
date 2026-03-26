#!/usr/bin/env python3
"""
Finds public API members missing KDoc documentation.

Parses binary-compatibility-validator .api dump files to extract public
classes/interfaces/functions, then checks corresponding Kotlin source
files for the presence of KDoc (/** ... */) comments preceding each declaration.

Skips:
  - override methods/properties (KDoc belongs on the interface/superclass)
  - Auto-generated members (equals, hashCode, toString, copy, componentN, etc.)
  - Synthetic / mangled members
  - Companion, DefaultImpls, $$serializer inner classes

Usage:
  python3 ${CLAUDE_SKILL_DIR}/scripts/find-missing-kdoc.py [--verbose]
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

# Navigate from .claude/skills/public-api-documenter/scripts/ up to project root
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
VERBOSE = "--verbose" in sys.argv

MODULES = ["kotlin-sdk-core", "kotlin-sdk-client", "kotlin-sdk-server", "kotlin-sdk-testing"]
SOURCE_VARIANTS = ["commonMain", "jvmMain", "nativeMain", "jsMain", "wasmJsMain"]

# Methods auto-generated or inherited that don't need KDoc
SKIP_METHODS = {
    "equals", "hashCode", "toString", "values", "valueOf", "serializer",
    "write$Self", "childSerializers", "deserialize", "serialize",
    "getDescriptor", "typeParametersSerializers",
}

SKIP_PREFIXES = ("get", "set", "is", "component", "copy", "<")


@dataclass
class ApiDecl:
    kind: str        # "class", "interface", "fun"
    jvm_name: str    # Full JVM internal name for classes, method name for functions
    simple_name: str
    owner_jvm: str | None  # Enclosing class JVM name


@dataclass
class MissingKdoc:
    module: str
    file: str
    line: int
    kind: str
    name: str


def parse_api_file(api_path: Path) -> list[ApiDecl]:
    """Parse a .api dump file and extract public declarations."""
    declarations = []
    class_stack: list[str] = []

    class_re = re.compile(
        r'^public\s+(?:(?:final|abstract|static|synthetic)\s+)*'
        r'(class|interface)\s+([\w/$]+)'
    )
    fun_re = re.compile(
        r'^public\s+(?:(?:final|abstract|static|synthetic)\s+)*'
        r'fun\s+(\S+)\s*\('
    )

    for raw_line in api_path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("//"):
            continue

        if line == "}":
            if class_stack:
                class_stack.pop()
            continue

        # Class/interface
        m = class_re.match(line)
        if m:
            kind = m.group(1)
            jvm_name = m.group(2)
            simple = jvm_name.split("/")[-1].split("$")[-1]

            class_stack.append(jvm_name)

            # Skip synthetic, Companion, DefaultImpls, $$serializer
            if "synthetic" in line:
                continue
            if simple in ("Companion", "DefaultImpls") or "$$serializer" in jvm_name:
                continue
            if "$" in simple:
                continue

            owner = class_stack[-2] if len(class_stack) >= 2 else None
            declarations.append(ApiDecl(kind, jvm_name, simple, owner))
            continue

        # Function
        m = fun_re.match(line)
        if m:
            if "synthetic" in line:
                continue
            method_name = m.group(1)

            # Skip generated/inherited
            if method_name in SKIP_METHODS:
                continue
            if any(method_name.startswith(p) for p in SKIP_PREFIXES):
                continue
            # Skip mangled names (contain hyphen from inline class)
            if "-" in method_name:
                continue

            owner = class_stack[-1] if class_stack else None
            fq = f"{owner}/{method_name}" if owner else method_name
            declarations.append(ApiDecl("fun", fq, method_name, owner))
            continue

    return declarations


def find_source_file(module: str, jvm_name: str) -> Path | None:
    """Find the Kotlin source file for a JVM class name."""
    parts = jvm_name.split("/")
    package_path = "/".join(parts[:-1])
    class_name = parts[-1]
    top_class = class_name.split("$")[0]

    for variant in SOURCE_VARIANTS:
        base = PROJECT_ROOT / module / "src" / variant / "kotlin" / package_path
        if not base.is_dir():
            continue

        # Direct match
        direct = base / f"{top_class}.kt"
        if direct.is_file():
            return direct

        # Search .kt files for declaration
        for kt_file in sorted(base.glob("*.kt")):
            text = kt_file.read_text()
            patterns = [
                f"class {top_class}",
                f"interface {top_class}",
                f"object {top_class}",
                f"enum class {top_class}",
            ]
            for pat in patterns:
                if pat in text:
                    return kt_file
    return None


def find_toplevel_file(module: str, facade_jvm: str) -> Path | None:
    """Find source file for a top-level function's facade class (e.g. FooKt -> Foo.kt)."""
    parts = facade_jvm.split("/")
    package_path = "/".join(parts[:-1])
    facade_name = parts[-1]
    file_stem = facade_name.removesuffix("Kt") if facade_name.endswith("Kt") else facade_name

    for variant in SOURCE_VARIANTS:
        base = PROJECT_ROOT / module / "src" / variant / "kotlin" / package_path
        if not base.is_dir():
            continue
        candidate = base / f"{file_stem}.kt"
        if candidate.is_file():
            return candidate
    return None


# Cache for file contents
_file_cache: dict[Path, list[str]] = {}


def get_lines(path: Path) -> list[str]:
    if path not in _file_cache:
        _file_cache[path] = path.read_text().splitlines()
    return _file_cache[path]


def find_decl_line(lines: list[str], kind: str, name: str) -> int | None:
    """Find 1-based line number of a public declaration. Prefers public over private matches."""
    if kind in ("class", "interface"):
        pattern = re.compile(
            rf'(?:^|\s)(?:public\s+)?(?:(?:data|sealed|enum|value|abstract|open|final|'
            rf'internal|expect|actual|annotation|private|protected)\s+)*'
            rf'(?:class|interface)\s+{re.escape(name)}\b'
        )
    elif kind == "fun":
        pattern = re.compile(
            rf'(?:^|\s)(?:public\s+)?(?:(?:suspend|inline|override|operator|infix|tailrec|'
            rf'external|open|final|expect|actual|internal|private|protected)\s+)*'
            rf'fun\s+(?:<[^>]+>\s+)?(?:\S+\.)?{re.escape(name)}\b'
        )
    else:
        pattern = re.compile(rf'\b{re.escape(name)}\b')

    # Collect all matches, prefer public declarations over private/internal
    private_re = re.compile(r'\b(private|internal)\b')
    first_match = None
    for i, line in enumerate(lines):
        if pattern.search(line):
            if not private_re.search(line):
                return i + 1  # public match — return immediately
            if first_match is None:
                first_match = i + 1  # save as fallback
    return first_match


def is_override(lines: list[str], decl_line: int) -> bool:
    """Check if the declaration at decl_line (1-based) has the 'override' modifier."""
    line = lines[decl_line - 1]
    return bool(re.search(r'\boverride\b', line))


def has_kdoc(lines: list[str], decl_line: int) -> bool:
    """Check if the declaration at decl_line (1-based) is preceded by a KDoc comment."""
    i = decl_line - 2  # 0-based index of line before declaration
    in_annotation = False
    while i >= 0:
        stripped = lines[i].strip()
        # Skip blank lines and single-line comments
        if not stripped or stripped.startswith("//"):
            i -= 1
            continue
        # Track multi-line annotation blocks: skip from closing ')' back to '@'
        if in_annotation:
            if stripped.startswith("@"):
                in_annotation = False
            i -= 1
            continue
        # Single-line annotation
        if stripped.startswith("@"):
            i -= 1
            continue
        # Start of a multi-line annotation (closing paren or string continuation)
        if stripped == ")" or stripped.endswith(",") or stripped.endswith(")"):
            # Check if we're inside a multi-line annotation
            # Walk back to see if there's an '@' line above
            j = i - 1
            while j >= 0:
                s = lines[j].strip()
                if not s:
                    j -= 1
                    continue
                if s.startswith("@"):
                    in_annotation = False
                    i = j - 1
                    break
                if s.endswith("*/"):
                    # Found KDoc before annotation block
                    return True
                j -= 1
                continue
            else:
                i -= 1
                continue
            continue
        # Check for KDoc end
        return stripped.endswith("*/")
    return False


def main():
    all_missing: list[MissingKdoc] = []
    total_checked = 0
    total_skipped_override = 0

    for module in MODULES:
        api_file = PROJECT_ROOT / module / "api" / f"{module}.api"
        if not api_file.exists():
            print(f"WARN: {api_file} not found, skipping", file=sys.stderr)
            continue

        declarations = parse_api_file(api_file)
        if VERBOSE:
            print(f"Module {module}: {len(declarations)} public declarations", file=sys.stderr)

        for decl in declarations:
            # Determine which file to look in
            if decl.kind == "fun":
                if decl.owner_jvm:
                    if decl.owner_jvm.endswith("Kt"):
                        src = find_toplevel_file(module, decl.owner_jvm)
                    else:
                        src = find_source_file(module, decl.owner_jvm)
                else:
                    continue
            else:
                src = find_source_file(module, decl.jvm_name)

            if src is None:
                if VERBOSE:
                    print(f"  SKIP (file not found): {decl.kind} {decl.simple_name}", file=sys.stderr)
                continue

            lines = get_lines(src)
            line_num = find_decl_line(lines, decl.kind, decl.simple_name)
            if line_num is None:
                if VERBOSE:
                    print(f"  SKIP (decl not found): {decl.kind} {decl.simple_name} in {src.name}", file=sys.stderr)
                continue

            # Skip override methods/properties — KDoc belongs on the interface/superclass
            if is_override(lines, line_num):
                total_skipped_override += 1
                if VERBOSE:
                    print(f"  SKIP (override): {decl.kind} {decl.simple_name} in {src.name}", file=sys.stderr)
                continue

            total_checked += 1

            if not has_kdoc(lines, line_num):
                rel = src.relative_to(PROJECT_ROOT)
                all_missing.append(MissingKdoc(module, str(rel), line_num, decl.kind, decl.simple_name))

    # Output grouped by module and file
    by_module: dict[str, list[MissingKdoc]] = {}
    for m in all_missing:
        by_module.setdefault(m.module, []).append(m)

    for mod in MODULES:
        items = by_module.get(mod, [])
        if not items:
            print(f"\n## {mod} — all documented")
            continue
        print(f"\n## {mod} ({len(items)} missing)")
        by_file: dict[str, list[MissingKdoc]] = {}
        for item in items:
            by_file.setdefault(item.file, []).append(item)
        for file_path in sorted(by_file):
            file_items = sorted(by_file[file_path], key=lambda x: x.line)
            print(f"  {file_path}")
            for item in file_items:
                print(f"    L{item.line}: {item.kind} `{item.name}`")

    print(f"\n---")
    print(f"Checked: {total_checked} | Skipped (override): {total_skipped_override}")
    print(f"Missing KDoc: {len(all_missing)}")


if __name__ == "__main__":
    main()
