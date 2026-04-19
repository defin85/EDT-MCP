#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS_DIR = ROOT / "mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl"
TESTS_DIR = ROOT / "mcp/tests/com.ditrix.edt.mcp.server.tests/src"
README = ROOT / "README.md"
OUTPUT = ROOT / "docs/agent/generated-tool-catalog.md"

LONG_RUNNING_TOOLS = {
    "clean_project",
    "debug_launch",
    "get_active_operation",
    "get_operation_snapshot",
    "revalidate_objects",
    "update_database",
}


def camel_to_snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def discover_tools() -> list[dict[str, str]]:
    tools: list[dict[str, str]] = []
    for path in sorted(TOOLS_DIR.glob("*Tool.java")):
        class_name = path.stem
        tool_name = camel_to_snake(class_name.removesuffix("Tool"))
        tests = sorted(TESTS_DIR.rglob(f"{class_name}Test.java"))
        test_ref = ", ".join(str(p.relative_to(ROOT)) for p in tests) if tests else "-"
        if tool_name in LONG_RUNNING_TOOLS:
            zone = "long-running-runtime"
            notes = "`docs/agent/long-running-ops.md`"
        elif tool_name.startswith(("get_", "list_")):
            zone = "read/discovery"
            notes = "-"
        elif tool_name.startswith(("rename_", "delete_", "add_", "write_")):
            zone = "mutation/refactoring"
            notes = "-"
        elif tool_name in {"validate_query", "find_references", "go_to_definition", "get_symbol_info"}:
            zone = "analysis/navigation"
            notes = "-"
        else:
            zone = "mixed"
            notes = "-"
        tools.append(
            {
                "tool_name": tool_name,
                "class_name": class_name,
                "impl_path": str(path.relative_to(ROOT)),
                "tests": test_ref,
                "zone": zone,
                "notes": notes,
            }
        )
    return tools


def parse_readme_tool_names() -> list[str]:
    lines = README.read_text(encoding="utf-8").splitlines()
    in_table = False
    names: list[str] = []
    for line in lines:
        if line.strip() == "## Available Tools":
            in_table = True
            continue
        if in_table and line.startswith("### "):
            break
        if in_table and line.startswith("| `"):
            match = re.match(r"\| `([^`]+)` \|", line)
            if match:
                names.append(match.group(1))
    return names


def render_markdown(tools: list[dict[str, str]], readme_names: list[str]) -> str:
    code_names = [tool["tool_name"] for tool in tools]
    missing_in_readme = sorted(set(code_names) - set(readme_names))
    missing_in_code = sorted(set(readme_names) - set(code_names))

    lines = [
        "<!-- GENERATED FILE: do not edit manually. Run `python3 scripts/generate_agent_refs.py`. -->",
        "# Generated Tool Catalog",
        "",
        "Этот файл генерируется из `tools/impl/*Tool.java` и служит fast reference для Codex.",
        "",
        f"- Tool implementations found: `{len(code_names)}`",
        f"- Tool names documented in `README.md`: `{len(readme_names)}`",
        f"- Drift status: `missing_in_readme={len(missing_in_readme)}`, `missing_in_code={len(missing_in_code)}`",
        "",
        "## Tool Map",
        "",
        "| Tool | Implementation | Primary tests | Zone | Notes |",
        "|------|----------------|---------------|------|-------|",
    ]
    for tool in tools:
        lines.append(
            f"| `{tool['tool_name']}` | `{tool['impl_path']}` | `{tool['tests']}` | `{tool['zone']}` | {tool['notes']} |"
        )

    lines.extend(
        [
            "",
            "## Drift Check",
            "",
            f"- Missing in `README.md`: {', '.join(f'`{name}`' for name in missing_in_readme) if missing_in_readme else 'none'}",
            f"- Missing in code scan: {', '.join(f'`{name}`' for name in missing_in_code) if missing_in_code else 'none'}",
            "",
            "## Regeneration",
            "",
            "```bash",
            "python3 scripts/generate_agent_refs.py",
            "```",
        ]
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if generated output is stale or README drifts from code")
    args = parser.parse_args()

    tools = discover_tools()
    readme_names = parse_readme_tool_names()
    rendered = render_markdown(tools, readme_names)
    missing_in_readme = sorted(set(tool["tool_name"] for tool in tools) - set(readme_names))
    missing_in_code = sorted(set(readme_names) - set(tool["tool_name"] for tool in tools))

    if args.check:
        if not OUTPUT.exists():
            print(f"missing generated file: {OUTPUT}", file=sys.stderr)
            return 1
        current = OUTPUT.read_text(encoding="utf-8")
        if current != rendered:
            print("generated tool catalog is stale; run python3 scripts/generate_agent_refs.py", file=sys.stderr)
            return 1
        if missing_in_readme or missing_in_code:
            print(
                f"tool drift detected: missing_in_readme={missing_in_readme}, missing_in_code={missing_in_code}",
                file=sys.stderr,
            )
            return 1
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(rendered, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
