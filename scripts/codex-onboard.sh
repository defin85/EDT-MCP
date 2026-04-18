#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

check_tool() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    printf 'ok   %s -> %s\n' "$name" "$(command -v "$name")"
  else
    printf 'miss %s\n' "$name"
  fi
}

echo "EDT-MCP Codex onboarding"
echo "root: $ROOT_DIR"
echo
echo "instruction chain:"
echo "1. AGENTS.md"
echo "2. docs/agent/index.md"
echo "3. docs/agent/architecture-map.md"
echo "4. docs/agent/verification.md"
echo "5. docs/agent/long-running-ops.md"
echo "6. docs/agent/generated-tool-catalog.md"
echo
echo "tooling:"
check_tool rg
check_tool java
check_tool mvn
check_tool python3
check_tool openspec
check_tool bd
echo
echo "quick checks:"
echo "- bash scripts/verify_agent_surface.sh"
echo "- mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile"
echo "- mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C"
